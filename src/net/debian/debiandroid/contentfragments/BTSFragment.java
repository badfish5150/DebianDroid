
package net.debian.debiandroid.contentfragments;

import java.util.ArrayList;
import java.util.HashMap;

import net.debian.debiandroid.AutoGroupCollapseListener;
import net.debian.debiandroid.DExpandableAdapter;
import net.debian.debiandroid.ItemFragment;
import net.debian.debiandroid.R;
import net.debian.debiandroid.SettingsActivity;
import net.debian.debiandroid.apiLayer.BTS;
import net.debian.debiandroid.utils.SearchCacher;
import net.debian.debiandroid.utils.UIUtils;
import net.debian.debiandroid.view.SearchBarView;
import net.debian.debiandroid.view.SearchBarView.OnSearchActionListener;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.Spinner;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.uberspot.storageutils.Cacher;

public class BTSFragment extends ItemFragment {

    private Spinner spinner;
    private int searchOptionSelected;
    private SearchBarView btsSearchBar;
    private ExpandableListView bugList;

    private BTS bts;
    private Context context;

    private ArrayList<String> bugListParentItems;
    private ArrayList<Object> bugListChildItems;

    private String[] spinnerValues;

    /** ID for the (un)subscribe menu item. It starts from +2
     * because the settings icon is in the +1 position */
    public static final int SUBSCRIPTION_ID = Menu.FIRST + 2;
    public static final int REFRESH_ID = Menu.FIRST + 3;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getSherlockActivity().getApplicationContext();
        bts = new BTS(context);
        if (SearchCacher.hasAnyLastSearch()) {
            new SearchBugInfoTask().execute();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.bts_fragment, container, false);

        setHasOptionsMenu(true);
        getSherlockActivity().getSupportActionBar().setTitle(R.string.search_bugs);

        searchOptionSelected = PreferenceManager.getDefaultSharedPreferences(context).getInt(
                "btsSearchOptionPos", 0);
        bugList = (ExpandableListView) rootView.findViewById(R.id.btsList);

        // Add autocollapsing of list if enabled
        if (SettingsActivity.isAutoCollapseEnabled(getSherlockActivity())) {
            bugList.setOnGroupExpandListener(new AutoGroupCollapseListener(bugList));
        }

        // Find the Views once in OnCreate to save time and not use findViewById later.
        spinner = (Spinner) rootView.findViewById(R.id.btsSpinner);
        spinnerValues = new String[] { getString(R.string.by_number), getString(R.string.in_package),
                getString(R.string.in_pckgs_maint_by), getString(R.string.submitted_by),
                getString(R.string.with_status) };
        setupSpinner();

        btsSearchBar = (SearchBarView) rootView.findViewById(R.id.btsSearchBarView);
        btsSearchBar.setHintAndType(R.string.bts_search_hint, InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);

        btsSearchBar.setOnSearchActionListener(new OnSearchActionListener() {

            @Override
            public void onSearchAction(String searchInput) {
                if ((searchInput != null) && !searchInput.trim().equals("")) {
                    SearchCacher
                            .setLastBugSearch(optionSelectedToBTSParam(searchOptionSelected), searchInput);
                    new SearchBugInfoTask().execute();
                }
            }
        });

        return rootView;
    }

    /** Initializes the spinner view and fills it with pts search choices */
    private void setupSpinner() {
        spinner.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.simple_spinner_list_child,
                spinnerValues));

        spinner.setSelection(searchOptionSelected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                searchOptionSelected = pos;
                // modify input type in searchBar
                btsSearchBar.setInputType(optionSelectedToInputType(pos));
                //Save change in preferences with storageutils
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putInt("btsSearchOptionPos", searchOptionSelected).commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
    }

    /**
     *
     * @param option based on the positions in spinnerValues
     * @return
     */
    private static String optionSelectedToBTSParam(int option) {
        if (option == 1) {
            return BTS.PACKAGE;
        } else if (option == 2) {
            return BTS.MAINT;
        } else if (option == 3) {
            return BTS.SUBMITTER;
        } else if (option == 4) {
            return BTS.STATUS;
        } else if (option == 0) {
            return BTS.BUGNUMBER;
        }
        return "";
    }

    private static int optionSelectedToInputType(int option) {
        if (option == 1) {
            return InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;
        } else if (option == 2) {
            return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        } else if (option == 3) {
            return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        } else if (option == 4) {
            return InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;
        } else if (option == 0) {
            return InputType.TYPE_CLASS_NUMBER;
        }
        return InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;
    }

    private String BTSParamToSpinnerOption(String param) {
        if (param.equals(BTS.PACKAGE)) {
            return getString(R.string.in_package);
        } else if (param.equals(BTS.MAINT)) {
            return getString(R.string.in_pckgs_maint_by);
        } else if (param.equals(BTS.SUBMITTER)) {
            return getString(R.string.submitted_by);
        } else if (param.equals(BTS.STATUS)) {
            return getString(R.string.with_status);
        } else if (param.equals(BTS.BUGNUMBER)) {
            return getString(R.string.by_number);
        }
        return "";
    }

    public void setupBugsList() {
        bugList.setDividerHeight(1);
        bugList.setClickable(true);

        final DExpandableAdapter adapter = new DExpandableAdapter(bugListParentItems, bugListChildItems);
        adapter.setInflater((LayoutInflater) getSherlockActivity().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE));
        bugList.setAdapter(adapter);
        registerForContextMenu(bugList);

        bugList.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View childView, int flatPos, long id) {
                if (ExpandableListView.getPackedPositionType(id) == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                    long packedPos = ((ExpandableListView) parent).getExpandableListPosition(flatPos);
                    int groupPosition = ExpandableListView.getPackedPositionGroup(packedPos);
                    int childPosition = ExpandableListView.getPackedPositionChild(packedPos);

                    @SuppressWarnings("unchecked")
                    String text = ((ArrayList<String>) bugListChildItems.get(groupPosition))
                            .get(childPosition);
                    String subject = bugListParentItems.get(groupPosition);
                    String bugnum = subject.replace("[", "").replaceFirst("].*$", "");
                    UIUtils.forwardToMailApp(getSherlockActivity(), bugnum + "@bugs.debian.org", "Re: "
                            + subject.replaceFirst("\\[.*\\)", ""), text.replaceAll("\n", "\n>"));
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        //Add subscription icon
        MenuItem subMenuItem = menu.add(0, SUBSCRIPTION_ID, Menu.CATEGORY_SECONDARY, "");
        subMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        setSubscriptionIcon(subMenuItem);

        menu.add(0, REFRESH_ID, Menu.CATEGORY_ALTERNATIVE, R.string.refresh).setIcon(R.drawable.refresh)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        setSubscriptionIcon(menu.findItem(SUBSCRIPTION_ID));
        super.onPrepareOptionsMenu(menu);
    }

    public void setSubscriptionIcon(MenuItem subMenuItem) {
        String subscription = SearchCacher.getLastBugSearchOption() + "|"
                + SearchCacher.getLastBugSearchValue();
        if (SearchCacher.hasLastBugsSearch() && bts.isSubscribedTo(subscription)) {
            subMenuItem.setIcon(R.drawable.subscribed);
            subMenuItem.setTitle(R.string.unsubscribe);
        } else {
            subMenuItem.setIcon(R.drawable.unsubscribed);
            subMenuItem.setTitle(R.string.subscribe);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case SUBSCRIPTION_ID:
                String lastSearchOption = SearchCacher.getLastBugSearchOption();
                String subscription = lastSearchOption + "|" + SearchCacher.getLastBugSearchValue();
                if (lastSearchOption != null) {
                    if (bts.isSubscribedTo(subscription)) {
                        item.setIcon(R.drawable.unsubscribed);
                        item.setTitle(R.string.subscribe);
                        bts.removeSubscriptionTo(subscription);
                    } else {
                        item.setIcon(R.drawable.subscribed);
                        item.setTitle(R.string.unsubscribe);
                        bts.addSubscriptionTo(subscription);
                    }
                }
                return true;
            case REFRESH_ID:
                if (SearchCacher.hasAnyLastSearch()) {
                    new SearchBugInfoTask().execute(true);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    class SearchBugInfoTask extends AsyncTask<Boolean, Integer, Void> {

        private ProgressDialog progressDialog;
        private String progressMessage;
        private int bugCount = 0;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressMessage = getString(R.string.searching_info_please_wait);
            progressDialog = ProgressDialog.show(getSherlockActivity(), getString(R.string.searching),
                    progressMessage, true, false);
        }

        @Override
        protected Void doInBackground(Boolean... params) {
            //If called with execute(true) set the cache to always bring fresh results
            if ((params.length != 0) && params[0]) {
                Cacher.disableCache();
            }
            // search and set bug data
            bugListParentItems = new ArrayList<String>();
            bugListChildItems = new ArrayList<Object>();

            ArrayList<String> bugNums = new ArrayList<String>();
            //Do search and fill bug results table
            if (SearchCacher.hasLastBugsSearch()) {
                if (SearchCacher.getLastBugSearchOption().equals(BTS.BUGNUMBER)) {
                    bugNums.add(SearchCacher.getLastBugSearchValue());
                } else {
                    bugNums = bts.getBugs(new String[] { SearchCacher.getLastBugSearchOption() },
                            new String[] { SearchCacher.getLastBugSearchValue() });
                }
            } else if (SearchCacher.hasLastPckgSearch()) {
                bugNums = bts.getBugs(new String[] { BTS.PACKAGE },
                        new String[] { SearchCacher.getLastPckgName() });
            }

            if (bugNums.size() <= 0) {
                bugListParentItems.add(getString(R.string.no_info_found));
                bugListChildItems.add(new ArrayList<String>());
            }
            bugCount = bugNums.size();

            for (int i = 0; i < bugNums.size(); ++i) {
                ArrayList<HashMap<String, String>> mailLog = new ArrayList<HashMap<String, String>>(2);
                try {
                    //build array with mail log
                    mailLog = bts.getBugLog(Integer.parseInt(bugNums.get(i).trim()));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                int mailLogSize = mailLog.size();
                if (mailLogSize > 0) {
                    ArrayList<String> log = new ArrayList<String>(mailLogSize);
                    // Shows bugs in the format [bugNumber](mails sent for that bugnum) Subject of first mail
                    StringBuilder title = new StringBuilder("[");
                    title.append(bugNums.get(i));
                    title.append("](");
                    title.append(mailLogSize);
                    title.append(")");
                    title.append(mailLog.get(0).get("subject"));
                    bugListParentItems.add(title.toString());

                    for (HashMap<String, String> mail : mailLog) {
                        log.add(getString(R.string.bug_mail_format, mail.get("date"), mail.get("from"),
                                mail.get("body")));
                    }
                    bugListChildItems.add(log);
                }
                publishProgress(i);
            }

            if ((params.length != 0) && params[0]) {
                Cacher.enableCache();
            }
            return null;
        }

        @Override
        @SuppressLint("NewApi")
        protected void onPostExecute(Void result) {
            if ((progressDialog != null) && progressDialog.isShowing()) {
                try {
                    progressDialog.dismiss();
                } catch (IllegalArgumentException e) {
                    return;
                }
            } else {
                return;
            }
            if (SearchCacher.hasLastBugsSearch()) {
                btsSearchBar.getInputEditText().setText(SearchCacher.getLastBugSearchValue());
                spinner.setSelection(UIUtils.getValuePosition(spinnerValues,
                        BTSParamToSpinnerOption(SearchCacher.getLastBugSearchOption())));
            }
            // If in android 3+ update the action bar menu so that
            // the subscription icon is valid to the new search
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                getSherlockActivity().invalidateOptionsMenu();
            }
            UIUtils.hideSoftKeyboard(getActivity(), btsSearchBar.getInputEditText());

            setupBugsList();
        }

        @Override
        public void onProgressUpdate(Integer... args) {
            progressDialog.setMessage(progressMessage
                    + getString(R.string.bugs_retrieved, args[0] + "/" + bugCount));
        }
    }
}
