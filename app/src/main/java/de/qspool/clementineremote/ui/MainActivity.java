/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2013, Andreas Muttscheller <asfa194@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package de.qspool.clementineremote.ui;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedList;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.mediasession.ClementineMediaSessionNotification;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.ui.adapter.NavigationDrawerListAdapter;
import de.qspool.clementineremote.ui.fragments.DownloadsFragment;
import de.qspool.clementineremote.ui.fragments.GlobalSearchFragment;
import de.qspool.clementineremote.ui.fragments.LibraryFragment;
import de.qspool.clementineremote.ui.fragments.PlayerFragment;
import de.qspool.clementineremote.ui.fragments.PlaylistFragment;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;
import de.qspool.clementineremote.ui.settings.ClementineSettings;
import de.qspool.clementineremote.ui.widgets.SlidingTabLayout;
import de.qspool.clementineremote.utils.Utilities;

public class MainActivity extends AppCompatActivity {

    private final static String MENU_POSITION = "last_menu_position";

    private final String TAG = ((Object) this).getClass().getSimpleName();

    private SharedPreferences mSharedPref;

    private MainActivityHandler mHandler;

    private Toast mToast;

    private int mCurrentFragment;

    private LinkedList<Fragment> mFragments = new LinkedList<>();

    private Fragment mPlayerFragment;

    private RelativeLayout mDrawerMenu;

    private ListView mDrawerList;

    private DrawerLayout mDrawerLayout;

    private ActionBarDrawerToggle mDrawerToggle;

    private int mLastPosition = 1;

    private boolean mOpenConnectDialog = true;

    private boolean mInstanceSaved = false;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on if user has requested this in preferences
        if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean(SharedPreferencesKeys.SP_KEEP_SCREEN_ON, true)
                && Utilities.isRemoteConnected()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        setContentView(R.layout.activity_main);

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        /*
         * Define here the available fragments in the main layout
         */
        mFragments.add(new GlobalSearchFragment());
        mFragments.add(new PlayerFragment());
        mFragments.add(new PlaylistFragment());
        mFragments.add(new LibraryFragment());
        mFragments.add(new DownloadsFragment());

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        mDrawerMenu = (RelativeLayout) findViewById(R.id.drawer_menu_layout);
        mDrawerList = (ListView) findViewById(R.id.drawer_list);

        // Show Clementine with hostname and ip
        TextView clementineHost = (TextView) findViewById(R.id.drawer_menu_text1);
        TextView clementineIp = (TextView) findViewById(R.id.drawer_menu_text2);

        clementineHost.setText(String.format(getString(R.string.navigation_drawer_clementine_on), App.Clementine.getHostname()));
        clementineIp.setText(mSharedPref.getString(SharedPreferencesKeys.SP_KEY_IP, "") + ":" + mSharedPref
                .getString(SharedPreferencesKeys.SP_KEY_PORT, ""));

        if (findViewById(R.id.player_frame) != null) {
            mPlayerFragment = new PlayerFragment();
            getFragmentManager().beginTransaction().add(R.id.player_frame, mPlayerFragment)
                    .commit();
            mLastPosition = 2;
        }

        // Create the header adapter
        LinkedList<NavigationDrawerListAdapter.NavigationDrawerItem> drawerItems = new LinkedList<>();
        String[] itemNames = getResources().getStringArray(R.array.navigation_drawer_items);
        TypedArray itemIcons = getResources().obtainTypedArray(R.array.navigation_drawer_icons);

        for (int i=0;i<itemNames.length;i++) {
            String item = itemNames[i];
            Drawable icon;
            try {
                icon = itemIcons.getDrawable(i);
            } catch (Resources.NotFoundException e) {
                icon = null;
            }

            NavigationDrawerListAdapter.NavigationDrawerItem.Type t = item.isEmpty() ?
                    NavigationDrawerListAdapter.NavigationDrawerItem.Type.TYPE_SECTION :
                    NavigationDrawerListAdapter.NavigationDrawerItem.Type.TYPE_ITEM;
            drawerItems.add(new NavigationDrawerListAdapter.NavigationDrawerItem(item, icon, t));
        }
        itemIcons.recycle();

        NavigationDrawerListAdapter navigationDrawerListAdapter = new NavigationDrawerListAdapter(this, R.layout.item_drawer_list, drawerItems);

        mDrawerList.setAdapter(navigationDrawerListAdapter);
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
        mDrawerList.setDivider(null);
        mDrawerList.setDividerHeight(0);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.string.connectdialog_connect,  /* "open drawer" description */
                R.string.dialog_close  /* "close drawer" description */
        ) {
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.addDrawerListener(mDrawerToggle);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // When we have a download notifitication and it was clicked, show the download.
        if (getIntent().hasExtra(ClementineMediaSessionNotification.EXTRA_NOTIFICATION_ID)) {
            int id = getIntent()
                    .getIntExtra(ClementineMediaSessionNotification.EXTRA_NOTIFICATION_ID, 0);
            if (id == -1) {
                mLastPosition = 1;
            } else {
                mLastPosition = 4;
            }
        }
        selectItem(mLastPosition, 1);

        // Hide the tabs by default. It's the fragments responsibility to enable and disable them.
        SlidingTabLayout tabs = (SlidingTabLayout) findViewById(R.id.tabs);
        tabs.setVisibility(View.GONE);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();

        if (savedInstanceState != null && savedInstanceState.containsKey(MENU_POSITION)) {
            mLastPosition = savedInstanceState.getInt(MENU_POSITION);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(MENU_POSITION, mLastPosition);
        mInstanceSaved = true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);

        selectItem(mLastPosition, 1);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Check if the user has changed the preferences to keep the screen on
        if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean(SharedPreferencesKeys.SP_KEEP_SCREEN_ON, true)
                && Utilities.isRemoteConnected()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        mOpenConnectDialog = true;
        mInstanceSaved = false;

        // Check if we are still connected
        if (App.ClementineConnection == null
                || App.Clementine == null
                || !App.ClementineConnection.isConnected()) {
            Log.d(TAG, "onResume - disconnect");
            setResult(ConnectActivity.RESULT_DISCONNECT);
            finish();
        } else {
            Log.d(TAG, "onResume - start");
            // Set the handler
            mHandler = new MainActivityHandler(this);
            App.ClementineConnection.setUiHandler(mHandler);

            mDrawerList
                    .performItemClick(mDrawerList.getAdapter().getView(mLastPosition, null, null),
                            mLastPosition,
                            mDrawerList.getAdapter().getItemId(mLastPosition));
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mHandler = null;
        if (App.ClementineConnection != null) {
            App.ClementineConnection.setUiHandler(null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // If we disconnected, open connectdialog
        if (App.ClementineConnection == null
                || App.Clementine == null
                || !App.ClementineConnection.isConnected()) {
            Log.d(TAG, "onDestroy - disconnect");
            if (mOpenConnectDialog) {
                Intent connectDialog = new Intent(this, ConnectActivity.class);
                connectDialog
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(connectDialog);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mFragments.get(mCurrentFragment).onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int currentVolume = App.Clementine.getVolume();
            // Control the volume of clementine if enabled in the options
            if (mSharedPref.getBoolean(SharedPreferencesKeys.SP_KEY_USE_VOLUMEKEYS, true)) {
                int volumeInc = Integer.parseInt(
                        mSharedPref.getString(SharedPreferencesKeys.SP_VOLUME_INC,
                                Clementine.DefaultVolumeInc));
                switch (keyCode) {
                    case KeyEvent.KEYCODE_VOLUME_DOWN:
                        Message msgDown = Message.obtain();
                        msgDown.obj = ClementineMessageFactory
                                .buildVolumeMessage(App.Clementine.getVolume() - volumeInc);
                        App.ClementineConnection.mHandler.sendMessage(msgDown);
                        if (currentVolume >= volumeInc) {
                            currentVolume -= volumeInc;
                        } else {
                            currentVolume = 0;
                        }
                        makeToast(getString(R.string.playler_volume) + " " + currentVolume + "%",
                                Toast.LENGTH_SHORT);
                        return true;
                    case KeyEvent.KEYCODE_VOLUME_UP:
                        Message msgUp = Message.obtain();
                        msgUp.obj = ClementineMessageFactory
                                .buildVolumeMessage(App.Clementine.getVolume() + volumeInc);
                        App.ClementineConnection.mHandler.sendMessage(msgUp);
                        if ((currentVolume + volumeInc) >= 100) {
                            currentVolume = 100;
                        } else {
                            currentVolume += volumeInc;
                        }
                        makeToast(getString(R.string.playler_volume) + " " + currentVolume + "%",
                                Toast.LENGTH_SHORT);
                        return true;
                    default:
                        break;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
        if (mSharedPref.getBoolean(SharedPreferencesKeys.SP_KEY_USE_VOLUMEKEYS, true)) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, keyEvent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (mDrawerLayout.isDrawerOpen(mDrawerMenu)) {
                mDrawerLayout.closeDrawer(mDrawerMenu);
            } else {
                mDrawerLayout.openDrawer(mDrawerMenu);
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Let the fragment handle the back button first
        if (mFragments.get(mCurrentFragment) != null &&
            mFragments.get(mCurrentFragment).isVisible()) {
            if (!((BackPressHandleable) mFragments.get(mCurrentFragment)).onBackPressed())
                super.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Request a disconnect from clementine
     */
    private void requestDisconnect() {
        // Move the request to the message
        Message msg = Message.obtain();
        msg.obj = ClementineMessage.getMessage(MsgType.DISCONNECT);

        // Send the request to the thread
        App.ClementineConnection.mHandler.sendMessage(msg);
    }


    /**
     * Disconnect was finished, now finish this activity
     */
    void disconnect() {
        makeToast(R.string.player_disconnected, Toast.LENGTH_SHORT);
        if (mOpenConnectDialog) {
            setResult(ConnectActivity.RESULT_DISCONNECT);
        } else {
            setResult(ConnectActivity.RESULT_QUIT);
        }
        mLastPosition = 0;
        finish();
    }

    /**
     * We got a message from Clementine. Here we process it for the main activity
     * and pass the data to the currently active fragment.
     * Info: Errormessages were already parsed in PlayerHandler!
     *
     * @param clementineMessage The message from Clementine
     */
    void MessageFromClementine(ClementineMessage clementineMessage) {
        // Update the Player Fragment
        if (mFragments.get(mCurrentFragment) != null &&
                mFragments.get(mCurrentFragment).isVisible() &&
                mFragments.get(mCurrentFragment).isAdded()) {
            ((RemoteDataReceiver)mFragments.get(mCurrentFragment)).MessageFromClementine(clementineMessage);
        }

        if (mPlayerFragment != null) {
            ((RemoteDataReceiver)mPlayerFragment).MessageFromClementine(clementineMessage);
        }
    }

    /**
     * Show text in a toast. Cancels previous toast
     *
     * @param resId  The resource id
     * @param length length
     */
    private void makeToast(int resId, int length) {
        makeToast(getString(resId), length);
    }

    /**
     * Show text in a toast. Cancels previous toast
     *
     * @param text   The text to show
     * @param length length
     */
    private void makeToast(String text, int length) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(this, text, length);
        mToast.show();
    }

    private class DrawerItemClickListener implements OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position != mLastPosition) {
                selectItem(position, 300);
            }
        }
    }

    /**
     * Swaps fragments in the main content view
     */
    private void selectItem(final int position, int delay) {
        mDrawerLayout.closeDrawer(mDrawerMenu);

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                if (mInstanceSaved) {
                    return;
                }
                FragmentManager fragmentManager = getFragmentManager();
                FragmentTransaction ft = fragmentManager.beginTransaction();
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.setCustomAnimations(R.animator.anim_fade_in, R.animator.anim_fade_out);

                switch (position) {
                    case 0: // Global search
                        ft.replace(R.id.content_frame, mFragments.get(0)).commit();
                        mCurrentFragment = 0;
                        mLastPosition = position;
                        break;
                    case 1: // Player
                        if (mPlayerFragment != null) {
                            ft.replace(R.id.content_frame, mFragments.get(2)).commit();
                        } else {
                            ft.replace(R.id.content_frame, mFragments.get(1)).commit();
                        }
                        mCurrentFragment = 1;
                        mLastPosition = position;
                        break;
                    case 2: // Playlist
                        ft.replace(R.id.content_frame, mFragments.get(2)).commit();
                        mCurrentFragment = 2;
                        mLastPosition = position;
                        break;
                    case 3: // Library
                        ft.replace(R.id.content_frame, mFragments.get(3)).commit();
                        mCurrentFragment = 3;
                        mLastPosition = position;
                        break;
                    case 4: // Downloads
                        ft.replace(R.id.content_frame, mFragments.get(4)).commit();
                        mCurrentFragment = 4;
                        mLastPosition = position;
                        break;
                    case 5: // Header Settings
                        break;
                    case 6: // Settings
                        Intent settingsIntent = new Intent(MainActivity.this,
                                ClementineSettings.class);
                        startActivity(settingsIntent);
                        break;
                    case 7: // Header Disconnect
                        break;
                    case 8: // Quit
                        mOpenConnectDialog = false;
                        requestDisconnect();
                    default:
                        break;
                }
            }

        }, delay);
    }

}
