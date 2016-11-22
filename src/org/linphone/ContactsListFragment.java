package org.linphone;
/*
ContactsListFragment.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PresenceActivity;
import org.linphone.core.PresenceActivityType;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Log;
import org.linphone.ui.Numpad;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.app.Fragment;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AlphabetIndexer;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
@SuppressLint("DefaultLocale")
public class ContactsListFragment extends Fragment implements OnClickListener, OnItemClickListener, LinphoneCoreListener, AdapterView.OnItemLongClickListener {
	private LayoutInflater mInflater;
	private ListView contactsList;
	private TextView noSipContact, noContact;
	private ImageView allContacts, linphoneContacts, newContact, edit, selectAll, deselectAll, delete, cancel;
	private boolean onlyDisplayLinphoneContacts, isEditMode;
	private View allContactsSelected, linphoneContactsSelected;
	private LinearLayout editList, topbar;
	private int lastKnownPosition;
	private AlphabetIndexer indexer;
	private boolean editOnClick = false, editConsumed = false, onlyDisplayChatAddress = false;
	private String sipAddressToAdd;
	private ImageView clearSearchField;
	private EditText searchField;
	private Cursor searchCursor;

	private static ContactsListFragment instance;
	
	static final boolean isInstanciated() {
		return instance != null;
	}

	public static final ContactsListFragment instance() {
		return instance;
	}
	private boolean isFriendsAdded = false;
	private Button btn_refresh;

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		mInflater = inflater;
        View view = inflater.inflate(R.layout.contacts_list, container, false);
        
        if (getArguments() != null) {
	        editOnClick = getArguments().getBoolean("EditOnClick");
	        sipAddressToAdd = getArguments().getString("SipAddress");
	        
	        onlyDisplayChatAddress = getArguments().getBoolean("ChatAddressOnly");
        }
        
        noSipContact = (TextView) view.findViewById(R.id.noSipContact);
        noContact = (TextView) view.findViewById(R.id.noContact);
        
        contactsList = (ListView) view.findViewById(R.id.contactsList);
        contactsList.setOnItemClickListener(this);
		contactsList.setOnItemLongClickListener(this);
        
        allContacts = (ImageView) view.findViewById(R.id.all_contacts);
        allContacts.setOnClickListener(this);
        
        linphoneContacts = (ImageView) view.findViewById(R.id.linphone_contacts);
        linphoneContacts.setOnClickListener(this);

		allContactsSelected = view.findViewById(R.id.all_contacts_select);
		linphoneContactsSelected = view.findViewById(R.id.linphone_contacts_select);
        
        newContact = (ImageView) view.findViewById(R.id.newContact);
        newContact.setOnClickListener(this);
        newContact.setEnabled(LinphoneManager.getLc().getCallsNb() == 0);
        
        allContacts.setEnabled(onlyDisplayLinphoneContacts);
        linphoneContacts.setEnabled(!allContacts.isEnabled());

		selectAll = (ImageView) view.findViewById(R.id.select_all);
		selectAll.setOnClickListener(this);

		deselectAll = (ImageView) view.findViewById(R.id.deselect_all);
		deselectAll.setOnClickListener(this);

		delete = (ImageView) view.findViewById(R.id.delete);
		delete.setOnClickListener(this);

		editList = (LinearLayout) view.findViewById(R.id.edit_list);
		topbar = (LinearLayout) view.findViewById(R.id.top_bar);

		cancel = (ImageView) view.findViewById(R.id.cancel);
		cancel.setOnClickListener(this);

		edit = (ImageView) view.findViewById(R.id.edit);
		edit.setOnClickListener(this);
        
		clearSearchField = (ImageView) view.findViewById(R.id.clearSearchField);
		clearSearchField.setOnClickListener(this);
		
		searchField = (EditText) view.findViewById(R.id.searchField);
		searchField.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				searchContacts(searchField.getText().toString());
			}
		});
		btn_refresh = (Button) view.findViewById(R.id.btn_refresh);
		btn_refresh.setOnClickListener(this);
		return view;
    }

	public int getNbItemsChecked(){
		int size = contactsList.getAdapter().getCount();
		int nb = 0;
		for(int i=0; i<size; i++) {
			if(contactsList.isItemChecked(i)) {
				nb ++;
			}
		}
		return nb;
	}

	public void enabledDeleteButton(Boolean enabled){
		if(enabled){
			delete.setEnabled(true);
		} else {
			if (getNbItemsChecked() == 0){
				delete.setEnabled(false);
			}
		}
	}
	//lifeng: 这里是联系人界面上面按钮的点击操作
	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.select_all) {
			deselectAll.setVisibility(View.VISIBLE);
			selectAll.setVisibility(View.GONE);
			enabledDeleteButton(true);
			selectAllList(true);
			return;
		}
		if (id == R.id.deselect_all) {
			deselectAll.setVisibility(View.GONE);
			selectAll.setVisibility(View.VISIBLE);
			enabledDeleteButton(false);
			selectAllList(false);
			return;
		}

		if (id == R.id.cancel) {
			quitEditMode();
			return;
		}

		if (id == R.id.delete) {
			final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.delete_text));
			Button delete = (Button) dialog.findViewById(R.id.delete_button);
			Button cancel = (Button) dialog.findViewById(R.id.cancel);

			delete.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					removeContacts();
					dialog.dismiss();
					quitEditMode();
				}
			});

			cancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					dialog.dismiss();
					quitEditMode();
				}
			});
			dialog.show();
			return;
		}

		if (id == R.id.edit) {
			editList.setVisibility(View.VISIBLE);
			topbar.setVisibility(View.GONE);
			enabledDeleteButton(false);
			isEditMode = true;
		}
		
		if (id == R.id.all_contacts) {
			onlyDisplayLinphoneContacts = false;
			allContactsSelected.setVisibility(View.VISIBLE);
			allContacts.setEnabled(false);
			linphoneContacts.setEnabled(true);
			linphoneContactsSelected.setVisibility(View.INVISIBLE);
		} 
		else if (id == R.id.linphone_contacts) {
			allContactsSelected.setVisibility(View.INVISIBLE);
			linphoneContactsSelected.setVisibility(View.VISIBLE);
			linphoneContacts.setEnabled(false);
			allContacts.setEnabled(true);
			onlyDisplayLinphoneContacts = true;

		}

		if(isEditMode){
			deselectAll.setVisibility(View.GONE);
			selectAll.setVisibility(View.VISIBLE);
		}

		if (searchField.getText().toString().length() > 0) {
			searchContacts();
		} else {
			Log.i("onlick:-------------------------------------------");
			changeContactsAdapter();
		}

		if (id == R.id.newContact) {
			editConsumed = true;
			LinphoneActivity.instance().addContact(null, sipAddressToAdd);
		} 
		else if (id == R.id.clearSearchField) {
			searchField.setText("");
		}
		if (id == R.id.btn_refresh) {
			Log.i("refresh:----------------------------------------");
			invalidate();
		}
	}

	private void selectAllList(boolean isSelectAll){
		int size = contactsList.getAdapter().getCount();
		for(int i=0; i<size; i++) {
			contactsList.setItemChecked(i,isSelectAll);
		}
	}

	private void deleteExistingContact(Contact contact) {
		String select = ContactsContract.Data.CONTACT_ID + " = ?";
		String[] args = new String[] { contact.getID() };

		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
						.withSelection(select, args)
						.build()
		);

		try {
			getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
			ContactsManager.getInstance().removeAllFriends(contact);
		} catch (Exception e) {
			Log.w(e.getMessage() + ":" + e.getStackTrace());
		}
	}

	private void removeContacts(){
		int size = contactsList.getAdapter().getCount();
		for(int i=0; i<size; i++) {
			if(contactsList.isItemChecked(i)){
				Contact contact = (Contact) contactsList.getAdapter().getItem(i);
				deleteExistingContact(contact);
				ContactsManager.getInstance().removeContactFromLists(getActivity().getContentResolver(), contact);
			}
		}
	}

	public void quitEditMode(){
		isEditMode = false;
		editList.setVisibility(View.GONE);
		topbar.setVisibility(View.VISIBLE);
		invalidate();
		if(getResources().getBoolean(R.bool.isTablet)){
			displayFirstContact();
		}
	}

	public void displayFirstContact(){
		if(contactsList.getAdapter().getCount() > 0) {
			LinphoneActivity.instance().displayContact((Contact) contactsList.getAdapter().getItem(0), false);
		} else {
			LinphoneActivity.instance().displayEmptyFragment();
		}
	}

	private void searchContacts() {
		searchContacts(searchField.getText().toString());
	}

	private void searchContacts(String search) {
		if (search == null || search.length() == 0) {
//			Log.i("onsearch:-------------------------------------");
			changeContactsAdapter();	
			return;
		}
		changeContactsToggle();
		
		if (searchCursor != null) {
			searchCursor.close();
		}

		if(LinphoneActivity.instance().getResources().getBoolean(R.bool.use_linphone_friend)) {
			//searchCursor = Compatibility.getSIPContactsCursor(getActivity().getContentResolver(), search, ContactsManager.getInstance().getContactsId());
			//indexer = new AlphabetIndexer(searchCursor, Compatibility.getCursorDisplayNameColumnIndex(searchCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
			//contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			//contactsList.setAdapter(new ContactsListAdapter(null, searchCursor));
		} else{
			if (onlyDisplayLinphoneContacts) {
				searchCursor = Compatibility.getSIPContactsCursor(getActivity().getContentResolver(), search, ContactsManager.getInstance().getContactsId());
				indexer = new AlphabetIndexer(searchCursor, Compatibility.getCursorDisplayNameColumnIndex(searchCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
				contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
				contactsList.setAdapter(new ContactsListAdapter(null, searchCursor));
			} else {
				searchCursor = Compatibility.getContactsCursor(getActivity().getContentResolver(), search, ContactsManager.getInstance().getContactsId());
				contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
				indexer = new AlphabetIndexer(searchCursor, Compatibility.getCursorDisplayNameColumnIndex(searchCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
				contactsList.setAdapter(new ContactsListAdapter(null, searchCursor));
			}
		}
	}

	@Override
	public void authInfoRequested(LinphoneCore lc, String realm, String username, String Domain) {

	}

	@Override
	public void callStatsUpdated(LinphoneCore lc, LinphoneCall call, LinphoneCallStats stats) {

	}

	@Override
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf, String url) {

	}

	@Override
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
		PresenceActivityType presenceActivity = lf.getPresenceModel().getActivity().getType();
		String state = "offline";
		Log.i("notifyPresenceRecevied:presenceActivity:-------------------------------------------->"+presenceActivity);
		if (presenceActivity == PresenceActivityType.Online) {
			state = "online";
		} else if (presenceActivity == PresenceActivityType.Offline) {
			state = "offline";

		}
	}

	@Override
	public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {

	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneCall call, LinphoneAddress from, byte[] event) {

	}

	@Override
	public void transferState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State new_call_state) {

	}

	@Override
	public void infoReceived(LinphoneCore lc, LinphoneCall call, LinphoneInfoMessage info) {

	}

	@Override
	public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev, SubscriptionState state) {

	}

	@Override
	public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev, PublishState state) {

	}

	@Override
	public void show(LinphoneCore lc) {

	}

	@Override
	public void displayStatus(LinphoneCore lc, String message) {

	}

	@Override
	public void displayMessage(LinphoneCore lc, String message) {

	}

	@Override
	public void displayWarning(LinphoneCore lc, String message) {

	}

	@Override
	public void fileTransferProgressIndication(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, int progress) {

	}

	@Override
	public void fileTransferRecv(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, byte[] buffer, int size) {

	}

	@Override
	public int fileTransferSend(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, ByteBuffer buffer, int size) {
		return 0;
	}

	@Override
	public void globalState(LinphoneCore lc, LinphoneCore.GlobalState state, String message) {

	}

	@Override
	public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {

	}

	@Override
	public void configuringStatus(LinphoneCore lc, LinphoneCore.RemoteProvisioningState state, String message) {

	}

	@Override
	public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {

	}

	@Override
	public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {

	}

	@Override
	public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call, boolean encrypted, String authenticationToken) {

	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneEvent ev, String eventName, LinphoneContent content) {

	}

	@Override
	public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr) {

	}

	@Override
	public void ecCalibrationStatus(LinphoneCore lc, LinphoneCore.EcCalibratorStatus status, int delay_ms, Object data) {

	}

	@Override
	public void uploadProgressIndication(LinphoneCore lc, int offset, int total) {

	}

	@Override
	public void uploadStateChanged(LinphoneCore lc, LinphoneCore.LogCollectionUploadState state, String info) {

	}

	private void changeContactsAdapter() {
		changeContactsToggle();
		
		if (searchCursor != null) {
			searchCursor.close();
		}
		
		Cursor allContactsCursor = ContactsManager.getInstance().getAllContactsCursor();
		Cursor sipContactsCursor = ContactsManager.getInstance().getSIPContactsCursor();

		noSipContact.setVisibility(View.GONE);
		noContact.setVisibility(View.GONE);
		contactsList.setVisibility(View.VISIBLE);

		if(LinphoneActivity.instance().getResources().getBoolean(R.bool.use_linphone_friend)) {
			indexer = new AlphabetIndexer(allContactsCursor, Compatibility.getCursorDisplayNameColumnIndex(allContactsCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
			contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			contactsList.setAdapter(new ContactsListAdapter(ContactsManager.getInstance().getAllContacts(), allContactsCursor));
		} else {
			if (onlyDisplayLinphoneContacts) {
				if (sipContactsCursor != null && sipContactsCursor.getCount() == 0) {
					noSipContact.setVisibility(View.VISIBLE);
					contactsList.setVisibility(View.GONE);
					edit.setEnabled(false);
				} else if (sipContactsCursor != null) {
					indexer = new AlphabetIndexer(sipContactsCursor, Compatibility.getCursorDisplayNameColumnIndex(sipContactsCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
					contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
					contactsList.setAdapter(new ContactsListAdapter(ContactsManager.getInstance().getSIPContacts(), sipContactsCursor));
					edit.setEnabled(true);
					//lifeng:因为好友列表没有存储在数据库中,每次启动LINPHONE好友列表都会置零,所以在显示CONTACTS前,要将所有的CONTACTS添加为FRIEND
					int count = contactsList.getAdapter().getCount();
					Log.i("count:--------------->"+count);
					for (int i=0; i<count; i++) {
						Contact contact = (Contact)contactsList.getAdapter().getItem(i);
//						Log.i("sipcontacts cursor:-------------------------->"+ContactsManager.getInstance().getSIPContacts());
//						Contact contact = ContactsManager.getInstance().getSIPContacts().get(i);
						contact.refresh(getActivity().getContentResolver());
						Log.i("contact.getNumbersOrAddresses():------------------------>"+contact.getNumbersOrAddresses());
						for (String numberOrAddress : contact.getNumbersOrAddresses()) {
							if (!contact.hasFriends() && numberOrAddress.contains("@")) {
								ContactsManager.getInstance().createNewFriend(contact, numberOrAddress);
								Log.i("numberOrAddress1:------------------------------>"+numberOrAddress);
							} else if (contact.hasFriends() && numberOrAddress.contains("@")) {
								LinphoneFriend[] friends = LinphoneManager.getLcIfManagerNotDestroyedOrNull().getFriendList();
							}
						}
					}
				}
			} else {
				if (allContactsCursor != null && allContactsCursor.getCount() == 0) {
					noContact.setVisibility(View.VISIBLE);
					contactsList.setVisibility(View.GONE);
					edit.setEnabled(false);
				} else if (allContactsCursor != null) {
					indexer = new AlphabetIndexer(allContactsCursor, Compatibility.getCursorDisplayNameColumnIndex(allContactsCursor), " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
					contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
					contactsList.setAdapter(new ContactsListAdapter(ContactsManager.getInstance().getAllContacts(), allContactsCursor));
					edit.setEnabled(true);
				}
			}
		}
		ContactsManager.getInstance().setLinphoneContactsPrefered(onlyDisplayLinphoneContacts);

	}

	private void changeContactsToggle() {
		if (onlyDisplayLinphoneContacts) {
			allContacts.setEnabled(true);
			allContactsSelected.setVisibility(View.INVISIBLE);
			linphoneContacts.setEnabled(false);
			linphoneContactsSelected.setVisibility(View.VISIBLE);
		} else {
			allContacts.setEnabled(false);
			allContactsSelected.setVisibility(View.VISIBLE);
			linphoneContacts.setEnabled(true);
			linphoneContactsSelected.setVisibility(View.INVISIBLE);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		Contact contact = (Contact) adapter.getItemAtPosition(position);
		for (String numberOrAddress : contact.getNumbersOrAddresses()) {
			Log.i("numberOrAddress:-------------------------------->"+numberOrAddress);
			if (!numberOrAddress.contains("@"))
				LinphoneManager.getInstance().newOutgoingCall(numberOrAddress, null);
		}
	}

	
	@Override
	public void onResume() {
		instance = this;
		super.onResume();

		if (editConsumed) {
			editOnClick = false;
			sipAddressToAdd = null;
		}

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACTS_LIST);
			LinphoneActivity.instance().hideTabBar(false);
			onlyDisplayLinphoneContacts = ContactsManager.getInstance().isLinphoneContactsPrefered();
		}
		onlyDisplayLinphoneContacts = true;		//lifeng:强制只显示SIP CONTACTS

		changeContactsToggle();

		invalidate();
	}
	
	@Override
	public void onPause() {
		instance = null;
		if (searchCursor != null) {
			searchCursor.close();
		}
		super.onPause();
	}
	
	public void invalidate() {
		if (searchField != null && searchField.getText().toString().length() > 0) {
			searchContacts(searchField.getText().toString());
		} else {
			changeContactsAdapter();
		}
		contactsList.setSelectionFromTop(lastKnownPosition, 0);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		contactsList.getAdapter();
		Contact contact = (Contact) parent.getItemAtPosition(position);
		if (editOnClick) {
			editConsumed = true;
			LinphoneActivity.instance().editContact(contact, sipAddressToAdd);
		} else {
			lastKnownPosition = contactsList.getFirstVisiblePosition();
			LinphoneActivity.instance().displayContact(contact, onlyDisplayChatAddress);
		}
		return false;
	}

	class ContactsListAdapter extends BaseAdapter implements SectionIndexer {
		private int margin;
		private Bitmap bitmapUnknown;
		private List<Contact> contacts;
		private Cursor cursor;
		
		ContactsListAdapter(List<Contact> contactsList, Cursor c) {
			contacts = contactsList;
			cursor = c;

			margin = LinphoneUtils.pixelsToDpi(LinphoneActivity.instance().getResources(), 10);
			bitmapUnknown = BitmapFactory.decodeResource(LinphoneActivity.instance().getResources(), R.drawable.avatar);
		}
		
		public int getCount() {
			if(LinphoneActivity.instance().getResources().getBoolean(R.bool.use_linphone_friend)) {
				return LinphoneManager.getLc().getFriendList().length;
			} else {
				return cursor.getCount();
			}
		}

		public Object getItem(int position) {
			if (contacts == null || position >= contacts.size()) {
				return Compatibility.getContact(getActivity().getContentResolver(), cursor, position);
			} else {
				return contacts.get(position);
			}
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = null;
			Contact contact = null;
			do {
				contact = (Contact) getItem(position);
			} while (contact == null);
			
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.contact_cell, parent, false);
			}

			CheckBox delete = (CheckBox) view.findViewById(R.id.delete);
			
			TextView name = (TextView) view.findViewById(R.id.name);
			name.setText(contact.getName());

			LinearLayout separator = (LinearLayout) view.findViewById(R.id.separator);
			TextView separatorText = (TextView) view.findViewById(R.id.separator_text);
			if (getPositionForSection(getSectionForPosition(position)) != position) {
				separator.setVisibility(View.GONE);
			} else {
				separator.setVisibility(View.VISIBLE);
				separatorText.setText(String.valueOf(contact.getName().charAt(0)));
			}
			
			ImageView icon = (ImageView) view.findViewById(R.id.contact_picture);
			if (contact.getPhoto() != null) {
				icon.setImageBitmap(contact.getPhoto());
			} else if (contact.getPhotoUri() != null) {
				icon.setImageURI(contact.getPhotoUri());
			} else {
				icon.setImageResource(R.drawable.avatar);
			}

			if (isEditMode) {
				delete.setVisibility(View.VISIBLE);
				delete.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
						contactsList.setItemChecked(position, b);
						if(getNbItemsChecked() == getCount()){
							deselectAll.setVisibility(View.VISIBLE);
							selectAll.setVisibility(View.GONE);
							enabledDeleteButton(true);
						} else {
							if(getNbItemsChecked() == 0){
								deselectAll.setVisibility(View.GONE);
								selectAll.setVisibility(View.VISIBLE);
								enabledDeleteButton(false);
							} else {
								deselectAll.setVisibility(View.GONE);
								selectAll.setVisibility(View.VISIBLE);
								enabledDeleteButton(true);
							}
						}
					}
				});
				if(contactsList.isItemChecked(position)) {
					delete.setChecked(true);
				} else {
					delete.setChecked(false);
				}
			} else {
				delete.setVisibility(View.GONE);
			}
			
			ImageView friendStatus = (ImageView) view.findViewById(R.id.friendStatus);
			LinphoneFriend[] friends = LinphoneManager.getLcIfManagerNotDestroyedOrNull()/*(getLc()*/.getFriendList();

			if (!ContactsManager.getInstance().isContactPresenceDisabled() && friends != null) {
				friendStatus.setVisibility(View.VISIBLE);
				PresenceActivityType presenceActivity = null;
				Log.i("friends length:------------------->"+friends.length);
//				for (LinphoneFriend friend : friends) {
//					Log.i("friend.getRefKey():--------------------->"+friend.getRefKey());
//					Log.i("friend.getAddress():----------------------->"+friend.getAddress().asString());
//				}
//				Log.i("position:------------------------------------>"+position);
//				Log.i("friends[position].getPresenceModel():----------->"+friends[position].getPresenceModel());
				if (friends.length > position && friends[position].getPresenceModel() != null) {
					LinphoneFriend friend = friends[position];
					presenceActivity = friend.getPresenceModel().getActivity().getType();
				}
				Log.i("presenceActivity:-------------------------------------->"+presenceActivity);
				if (presenceActivity == PresenceActivityType.Online) {
					friendStatus.setImageResource(R.drawable.led_connected);
				} else if (presenceActivity == PresenceActivityType.Busy) {
					friendStatus.setImageResource(R.drawable.led_error);
				} else if (presenceActivity == PresenceActivityType.Away) {
					friendStatus.setImageResource(R.drawable.led_inprogress);
				} else if (presenceActivity == PresenceActivityType.Offline) {
					friendStatus.setImageResource(R.drawable.led_disconnected);
				}
				else {
//					Log.i("presenceActivity1:------------------------------>" + presenceActivity);
//					friendStatus.setImageResource(R.drawable.call_quality_indicator_0);
					friendStatus.setImageResource(R.drawable.led_disconnected);
				}
			}
			
			return view;
		}

		@Override
		public int getPositionForSection(int section) {
			return indexer.getPositionForSection(section);
		}

		@Override
		public int getSectionForPosition(int position) {
			return indexer.getSectionForPosition(position);
		}

		@Override
		public Object[] getSections() {
			return indexer.getSections();
		}
	}



}
