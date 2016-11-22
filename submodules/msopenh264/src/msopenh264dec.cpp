/*
H.264 encoder/decoder plugin for mediastreamer2 based on the openh264 library.
Copyright (C) 2006-2012 Belledonne Communications, Grenoble

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/


#include "msopenh264dec.h"
#include "mediastreamer2/msticker.h"
#include "ortp/b64.h"
#include <android/log.h>
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "lifeng", __VA_ARGS__))

MSOpenH264Decoder::MSOpenH264Decoder(MSFilter *f)
	: mFilter(f), mDecoder(0), mUnpacker(0), mSPS(0), mPPS(0), mYUVMsg(0),
	mBitstream(0), mBitstreamSize(65536), mLastErrorReportTime(0),
	mWidth(MS_VIDEO_SIZE_UNKNOWN_W), mHeight(MS_VIDEO_SIZE_UNKNOWN_H),
	mInitialized(false), mFirstImageDecoded(false)
{
	long ret = WelsCreateDecoder(&mDecoder);
	if (ret != 0) {
		ms_error("OpenH264 decoder: Failed to create decoder: %li", ret);
	} else {
		mBitstream = static_cast<uint8_t *>(ms_malloc0(mBitstreamSize));
	}
}

MSOpenH264Decoder::~MSOpenH264Decoder()
{
	if (mBitstream != 0) {
		ms_free(mBitstream);
	}
	if (mDecoder != 0) {
		WelsDestroyDecoder(mDecoder);
	}
}

void MSOpenH264Decoder::initialize()
{
	if (!mInitialized) {
		mFirstImageDecoded = false;
		mUnpacker=rfc3984_new();
		if (mDecoder != 0) {
			SDecodingParam params = { 0 };
			params.eOutputColorFormat = videoFormatI420;
			params.uiTargetDqLayer = (unsigned char) -1;
			params.eEcActiveIdc = ERROR_CON_FRAME_COPY_CROSS_IDR;
			params.sVideoProperty.size = sizeof(params.sVideoProperty);
			params.sVideoProperty.eVideoBsType = VIDEO_BITSTREAM_DEFAULT;
			long ret = mDecoder->Initialize(&params);
			if (ret != 0) {
				ms_error("OpenH264 decoder: Failed to initialize: %li", ret);
			} else {
				ms_average_fps_init(&mFPS, "OpenH264 decoder: FPS=%f");
				mInitialized = true;
			}
		}
	}
}



/*********************************** lifeng **********************
 *
 *****************************************************************/
int MSOpenH264Decoder::PSToFrame(MSQueue *psq)
{
	uint8_t stuff_len = 0, pes_header_len;
//	uint8_t payload;
	uint16_t header_len = 0;
//	static uint16_t pes_packet_len = 0;
//	uint16_t rtp_payload_length = 0;
	static uint64_t idr_count = 0;
	mblk_t *im;
	static uint8_t *dst = mBitstream;
	uint8_t *end = mBitstream + mBitstreamSize;
//	static bool startPicture = false;
//	if (startPicture) startPicture = false;

	while ((im = ms_queue_get(psq)) != NULL) {
		uint8_t *src = im->b_rptr;
		uint8_t *dst_start = dst;
		int nalLen = im->b_wptr - src;
		if ((dst + nalLen + 128) > end) {
			int pos = dst - mBitstream;
			enlargeBitstream(mBitstreamSize + nalLen + 128);
			dst = mBitstream + pos;
			end = mBitstream + mBitstreamSize;
		}
		while (src < (im->b_wptr - 5)) {
			if (src[0] == 0x00 && src[1] == 0x00 && src[2] == 0x01 && src[3] == 0xba) {	//ps system header
				LOGI("src[0]:%x, src[1]:%x, src[2]:%x, src[3]:%x, src[4]:%x, src[5]:%x\n", src[0], src[1], src[2], src[3], src[4], src[5]);

				//跳过
				src += 4;	//跳过start code-00 00 01 ba
				src += 9;	//跳过scre & marker & scr base & mux rate
				stuff_len = *src & 0x7;	//获取填充字节数
				src += 1;	//跳过填充指示位(一般是f8)
				src += stuff_len;	//跳过填充的字节 //todo:这里应该可以去掉
			}
			else if (src[0] == 0x00 && src[1] == 0x00 && src[2] == 0x01 && src[3] == 0xbb) {	//ps system title header
//				printf("src[0]:%x, src[1]:%x, src[2]:%x, src[3]:%x, src[4]:%x, src[5]:%x\n", src[0], src[1], src[2], src[3], src[4], src[5]);

				//跳过
				src += 4;	//跳过00 00 01 bb
				header_len = (src[0] << 8)|src[1];
				src += 2;		//跳过header length 字节
				src += header_len; 	//跳过header length个字节
			}
			else if (src[0] == 0x00 && src[1] == 0x00 && src[2] == 0x01 && src[3] == 0xbc) {	//ps system map header
//				printf("src[0]:%x, src[1]:%x, src[2]:%x, src[3]:%x, src[4]:%x, src[5]:%x\n", src[0], src[1], src[2], src[3], src[4], src[5]);

				//跳过
				src += 4;	//跳过00 00 01 bc
				header_len = (src[0] << 8)|src[1]; //header_len经常等于00 1e
				src += 2;		//跳过header length字段
				src += 2;		//跳过这个字段(经常用不到,一般是e1 ff)
				src += 4;		//再跳过4个字节(有待确认这四个字节啥意思－找到138181的标准文档)
//				payload = getPayloadType(*src);
//				rtp_set_payload_type(m, payload);
				src++;		//跳过stream type字段
				src += (header_len-7);	//跳过header_lenght个字节
			}
			else if (0 == src[0] && 0 == src[1] && 0 == src[2] && 1 == src[3] && 0x67 == src[4]) {
					LOGI("SPS--->m[0]:%x, m[1]:%x, m[2]:%x, m[3]:%x, m[4]:%x ....????  \n", im->b_rptr[0], im->b_rptr[1], im->b_rptr[2], im->b_rptr[3], im->b_rptr[4]);
					dst = mBitstream;
					idr_count = 0;
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 1;
				*dst++ = src[4];
				src += 5;
			}
			else if (/*(0 == src[0] && 0 == src[1] && 0 == src[2] && 1 == src[3] && 0x67 == src[4]) ||*/
					 (0 == src[0] && 0 == src[1] && 0 == src[2] && 1 == src[3] && 0x68 == src[4]) ||
					 (0 == src[0] && 0 == src[1] && 0 == src[2] && 1 == src[3] && 6	   == src[4])){
				printf("src[0]:%x, src[1]:%x, src[2]:%x, src[3]:%x, src[4]:%x, src[5]:%x\n", src[0], src[1], src[2], src[3], src[4], src[5]);
//				if (0==src[0] && 0==src[1] && 0==src[2] && 1==src[3] && 0x67==src[4])
//					dst = mBitstream;
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 1;
				*dst++ = src[4];
				src += 5;
			}
			else if (0 == src[0] && 0 == src[1] && 0 == src[2] && 1 == src[3] && 0x65 == src[4]){
//				printf("src[0]:%x, src[1]:%x, src[2]:%x, src[3]:%x, src[4]:%x, src[5]:%x\n", src[0], src[1], src[2], src[3], src[4], src[5]);
				if (idr_count == 0)
					idr_count++;
				else
					dst = mBitstream;
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 1;
				*dst++ = src[4];
				src += 5;
			}
			else if (0 == src[0] && 0 == src[1] && 0 == src[2] && 1 == src[3] && 0x61 == src[4]){
//				printf("src[0]:%x, src[1]:%x, src[2]:%x, src[3]:%x, src[4]:%x, src[5]:%x\n", src[0], src[1], src[2], src[3], src[4], src[5]);
				dst = mBitstream;
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 1;
				*dst++ = src[4];
				src += 5;
			}

			else if (0 == src[0] && 0 == src[1] && 1 == src[2]) {	//pes
				//跳过pes
				src += 3;	//跳过pes start code:00 00 01
				src++;		//跳过stream id字段(e0)
//				pes_packet_len = (src[0] << 8)|src[1];
				src += 2;	//跳过packet length字段-注意:这个字段值可能错误，有些打包的包长度和这个值不一致
				src += 2;	//跳过2字节pes header识别符
				pes_header_len = *src++;
				src += pes_header_len;
			}
//			else if ((0 == src[0]) && (0 == src[1]) && (3 > src[2])) {
//				printf("ggggggggggggggggggggggggggggggggggggggggggggggggggggg\n");
//				*dst++ = 0;
//				*dst++ = 0;
//				*dst++ = 3;
//				src += 2;
//			}
			else {
				*dst++ = *src++;
			}
		}
		while (src < im->b_wptr) {
			*dst++ = *src++;
		}

		freemsg(im);
	}
	/************* lifeng:打印最后字段，看看有没有缺失*******/
//	printf("d1:%0x, d2:%0x, d3:%0x, d4:%0x, d5:%0x\n", *(dst-5), *(dst-4), *(dst-3), *(dst-2), *(dst-1));
//	printf("t length:%d\n", dst-mBitstream);
	return dst - mBitstream;
}

void MSOpenH264Decoder::feed()
{
	if (!isInitialized()){
		ms_error("MSOpenH264Decoder::feed(): not initialized");
		ms_queue_flush(mFilter->inputs[0]);
		return;
	}

	MSQueue nalus;
	ms_queue_init(&nalus);

	mblk_t *im;
	bool requestPLI = false;
	while ((im = ms_queue_get(mFilter->inputs[0])) != NULL) {
		if ((getIDRPicId() == 0) && (mSPS != 0) && (mPPS != 0)) {
			// Push the sps/pps given in sprop-parameter-sets if any
			mblk_set_timestamp_info(mSPS, mblk_get_timestamp_info(im));
			mblk_set_timestamp_info(mPPS, mblk_get_timestamp_info(im));
//			requestPLI |= (rfc3984_unpack(mUnpacker, mSPS, &nalus) < 0);  //lifeng:注释掉所有的unpacke-这里是大华的ps流解析后得到的裸数据
//			requestPLI |= (rfc3984_unpack(mUnpacker, mPPS, &nalus) < 0);
            mSPS = 0;
			mPPS = 0;
		}
//		requestPLI |= (rfc3984_unpack(mUnpacker, im, &nalus) < 0);
        ms_queue_put(&nalus, im);       //lifeng

		if (!ms_queue_empty(&nalus)) {
			void * pData[3] = { 0 };
			SBufferInfo sDstBufInfo = { 0 };
			int len =/*nalusToFrame(&nalus)*/PSToFrame(&nalus); 		//lifeng change
		if (mblk_get_marker_info(im) == 0) continue;		//lifeng:要组成帧才能送给openh264 decoder，否则解码出错
#if 0
		static FILE *fp = NULL;
		if (!fp) {
			fp = fopen("/sdcard/Pictures/bitstream-ps.h264", "wb");
		}
		if (fp) {
			fwrite(mBitstream, 1, len, fp);
		}
#endif
//			if (0==mBitstream[0] && 0==mBitstream[1] && 0==mBitstream[2] && 1==mBitstream[3] && 0x65==mBitstream[4])
//				LOGI("d[0]:0x%x, d[1]:0x%x, d[2]:0x%x, d[3]:0x%x, d[4]:0x%x, d[5]:0x%x\n",
//							mBitstream[0], mBitstream[1], mBitstream[2], mBitstream[3], mBitstream[4], mBitstream[5]);
			DECODING_STATE state = mDecoder->DecodeFrame2(mBitstream, len, (uint8_t**)pData, &sDstBufInfo);

			if (state != dsErrorFree) {
				ms_error("OpenH264 decoder: DecodeFrame2 failed: 0x%x", state);
				if (mAVPFEnabled) {
					requestPLI = true;
				} else if (((mFilter->ticker->time - mLastErrorReportTime) > 5000) || (mLastErrorReportTime == 0)) {
					mLastErrorReportTime = mFilter->ticker->time;
					ms_filter_notify_no_arg(mFilter, MS_VIDEO_DECODER_DECODING_ERRORS);
				}
			}
//			LOGI("value==============%d\n", sDstBufInfo.iBufferStatus);
			if (sDstBufInfo.iBufferStatus == 1) {
				uint8_t * pDst[3] = { 0 };
				pDst[0] = (uint8_t *)pData[0];
				pDst[1] = (uint8_t *)pData[1];
				pDst[2] = (uint8_t *)pData[2];

				// Update video size and (re)allocate YUV buffer if needed
				if ((mWidth != sDstBufInfo.UsrData.sSystemBuffer.iWidth)
					|| (mHeight != sDstBufInfo.UsrData.sSystemBuffer.iHeight)) {
					if (mYUVMsg) {
						freemsg(mYUVMsg);
					}
					mWidth = sDstBufInfo.UsrData.sSystemBuffer.iWidth;
					mHeight = sDstBufInfo.UsrData.sSystemBuffer.iHeight;
					mYUVMsg = ms_yuv_buf_alloc(&mOutbuf, mWidth, mHeight);
					ms_filter_notify_no_arg(mFilter,MS_FILTER_OUTPUT_FMT_CHANGED);
				}

				// Scale/copy frame to destination mblk_t
				for (int i = 0; i < 3; i++) {
					uint8_t *dst = mOutbuf.planes[i];
					uint8_t *src = pDst[i];
					int h = mHeight >> (( i > 0) ? 1 : 0);

					for(int j = 0; j < h; j++) {
						memcpy(dst, src, mOutbuf.strides[i]);
						dst += mOutbuf.strides[i];
						src += sDstBufInfo.UsrData.sSystemBuffer.iStride[(i == 0) ? 0 : 1];
					}
				}
				ms_queue_put(mFilter->outputs[0], dupmsg(mYUVMsg));
//				LOGI("mYUVMsg.b_rptr[0-4]====0x%llx\n", *(uint64_t*)(mYUVMsg->b_rptr));	//lifeng

				// Update average FPS
				if (ms_average_fps_update(&mFPS, mFilter->ticker->time)) {
					ms_message("OpenH264 decoder: Frame size: %dx%d", mWidth, mHeight);
				}

				// Notify first decoded image
				if (!mFirstImageDecoded) {
					mFirstImageDecoded = true;
					ms_filter_notify_no_arg(mFilter, MS_VIDEO_DECODER_FIRST_IMAGE_DECODED);
				}

#if MSOPENH264_DEBUG
				ms_message("OpenH264 decoder: IDR pic id: %d, Frame num: %d, Temporal id: %d, VCL NAL: %d", getIDRPicId(), getFrameNum(), getTemporalId(), getVCLNal());
#endif
			}
		}
	}

	if (mAVPFEnabled && requestPLI) {
		ms_filter_notify_no_arg(mFilter, MS_VIDEO_DECODER_SEND_PLI);
	}
}

void MSOpenH264Decoder::uninitialize()
{
	if (mSPS != 0) {
		freemsg(mSPS);
		mSPS=NULL;
	}
	if (mPPS != 0) {
		freemsg(mPPS);
		mPPS=NULL;
	}
	if (mYUVMsg != 0) {
		freemsg(mYUVMsg);
		mYUVMsg=NULL;
	}
	if (mDecoder != 0) {
		mDecoder->Uninitialize();
	}
	if (mUnpacker){
		rfc3984_destroy(mUnpacker);
		mUnpacker=NULL;
	}
	mInitialized = false;
}

void MSOpenH264Decoder::provideSpropParameterSets(char *value, int valueSize)
{
	char *b64_sps = value;
	char *b64_pps = strchr(value, ',');
	if (b64_pps) {
		*b64_pps = '\0';
		++b64_pps;
		ms_message("OpenH264 decoder: Got sprop-parameter-sets sps=%s, pps=%s", b64_sps, b64_pps);
		mSPS = allocb(valueSize, 0);
		mSPS->b_wptr += b64::b64_decode(b64_sps, strlen(b64_sps), mSPS->b_wptr, valueSize);
		mPPS = allocb(valueSize, 0);
		mPPS->b_wptr += b64::b64_decode(b64_pps, strlen(b64_pps), mPPS->b_wptr, valueSize);
	}
}

void MSOpenH264Decoder::resetFirstImageDecoded()
{
	mFirstImageDecoded = false;
	mWidth = MS_VIDEO_SIZE_UNKNOWN_W;
	mHeight = MS_VIDEO_SIZE_UNKNOWN_H;
}

MSVideoSize MSOpenH264Decoder::getSize() const
{
	MSVideoSize size;
	size.width = mWidth;
	size.height = mHeight;
	return size;
}

float MSOpenH264Decoder::getFps()const{
	return ms_average_fps_get(&mFPS);
}

const MSFmtDescriptor * MSOpenH264Decoder::getOutFmt()const{
	MSVideoSize vsize={mWidth,mHeight};
	return ms_factory_get_video_format(mFilter->factory,"YUV420P",vsize,0,NULL);
}

int MSOpenH264Decoder::nalusToFrame(MSQueue *nalus)
{
	mblk_t *im;
	uint8_t *dst = mBitstream;
	uint8_t *end = mBitstream + mBitstreamSize;
	bool startPicture = true;

	while ((im = ms_queue_get(nalus)) != NULL) {
		uint8_t *src = im->b_rptr;
		int nalLen = im->b_wptr - src;
		if ((dst + nalLen + 128) > end) {
			int pos = dst - mBitstream;
			enlargeBitstream(mBitstreamSize + nalLen + 128);
			dst = mBitstream + pos;
			end = mBitstream + mBitstreamSize;
		}
#if 0
		int size = im->b_wptr - src;
		printf("src[0]:%x, src[1]:%x, src[2]:%x, src[3]:%x, src[4]:%x, src[5]:%x\n", src[0], src[1], src[2], src[3], src[4], src[5]);

		while (src < (im->b_wptr - 5)) {
			if (0 == src[0] && 0 == src[1] && 1 == src[2] && 6 == src[3]) {	//sei
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 1;
				*dst++ = 6;
				src += 4;
			}
			else if ((0 == src[0] && 0 == src[1] && 0 == src[2] && 1 == src[3] && 0x67 == src[4]) ||
					 (0 == src[0] && 0 == src[1] && 0 == src[2] && 1 == src[3] && 0x68 == src[4])){
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 1;
				*dst++ = src[4];
				src += 5;
			}
			else if ((0 == src[0] && 0 == src[1] && 0 == src[2] && 1 == src[3] && 0x65 == src[4]) ||
					 (0 == src[0] && 0 == src[1] && 0 == src[2] && 1 == src[3] && 0x61 == src[4])) {
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 1;
				*dst++ = src[4];
				src += 5;
			}
			else if ((0 == src[0]) && (0 == src[1]) && (3 > src[2])) {
				*dst++ = 0;
				*dst++ = 0;
				*dst++ = 3;
				src += 2;
			}
			else {
				*dst++ = *src++;
			}

		}
		while (src < im->b_wptr) {
			*dst++ = *src++;
		}

#if 0
		static FILE *fp = NULL;
		if (!fp) {
			fp = fopen("bitstream-ps-0701.h264", "wb+");
		}
		if (fp) {
			fwrite(dst-size, 1, size, fp);
		}
#endif
#else
		if ((src[0] == 0) && (src[1] == 0) && (src[2] == 0) && (src[3] == 1)) {
			// Workaround for stupid RTP H264 sender that includes nal markers
#if MSOPENH264_DEBUG
			ms_warning("OpenH264 decoder: stupid RTP H264 encoder");
#endif
			int size = im->b_wptr - src;
			memcpy(dst, src, size);
			dst += size;
		} else {
			uint8_t naluType = *src & 0x1f;
#if MSOPENH264_DEBUG
			if ((naluType != 1) && (naluType != 7) && (naluType != 8)) {
				ms_message("OpenH264 decoder: naluType=%d", naluType);
			}
			if (naluType == 7) {
				ms_message("OpenH264 decoder: Got SPS");
			}
			if (naluType == 8) {
				ms_message("OpenH264 decoder: Got PPS");
			}
#endif
			if (startPicture
				|| (naluType == 6) // SEI
				|| (naluType == 7) // SPS
				|| (naluType == 8) // PPS
				|| ((naluType >= 14) && (naluType <= 18))) { // Reserved
				*dst++ = 0;
				startPicture = false;
			}

			// Prepend nal marker
			*dst++ = 0;
			*dst++ = 0;
			*dst++ = 1;
			*dst++ = *src++;
			while (src < (im->b_wptr - 3)) {
				if ((src[0] == 0) && (src[1] == 0) && (src[2] < 3)) {
					*dst++ = 0;
					*dst++ = 0;
					*dst++ = 3;
					src += 2;
				}
				*dst++ = *src++;
			}
			while (src < im->b_wptr) {
				*dst++ = *src++;
			}
		}
#endif
		freemsg(im);
	}
	return dst - mBitstream;
}

void MSOpenH264Decoder::enlargeBitstream(int newSize)
{
	mBitstreamSize = newSize;
	mBitstream = static_cast<uint8_t *>(ms_realloc(mBitstream, mBitstreamSize));
}

int32_t MSOpenH264Decoder::getFrameNum()
{
	int32_t frameNum = -1;
	int ret = mDecoder->GetOption(DECODER_OPTION_FRAME_NUM, &frameNum);
	if (ret != 0) {
		ms_error("OpenH264 decoder: Failed getting frame number: %d", ret);
	}
	return frameNum;
}

int32_t MSOpenH264Decoder::getIDRPicId()
{
	int32_t IDRPicId = -1;
	int ret = mDecoder->GetOption(DECODER_OPTION_IDR_PIC_ID, &IDRPicId);
	if (ret != 0) {
		ms_error("OpenH264 decoder: Failed getting IDR pic id: %d", ret);
	}
	return IDRPicId;
}

int32_t MSOpenH264Decoder::getTemporalId()
{
	int32_t temporalId = -1;
	int ret = mDecoder->GetOption(DECODER_OPTION_TEMPORAL_ID, &temporalId);
	if (ret != 0) {
		ms_error("OpenH264 decoder: Failed getting temporal id: %d", ret);
	}
	return temporalId;
}

int32_t MSOpenH264Decoder::getVCLNal()
{
	int32_t vclNal = -1;
	int ret = mDecoder->GetOption(DECODER_OPTION_VCL_NAL, &vclNal);
	if (ret != 0) {
		ms_error("OpenH264 decoder: Failed getting VCL NAL: %d", ret);
	}
	return vclNal;
}
