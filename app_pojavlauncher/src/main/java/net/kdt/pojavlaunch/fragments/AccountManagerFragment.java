package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcAccountSpinner;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.util.List;

/**
 * NovaCraft account dashboard. Authentication itself remains delegated to the
 * launcher's existing Microsoft and local account flows.
 */
public class AccountManagerFragment extends Fragment {
    public static final String TAG = "NOVACRAFT_ACCOUNT_MANAGER";

    private TextView mCurrentName;
    private TextView mCurrentType;
    private TextView mAccountCount;
    private TextView mEmptyState;
    private LinearLayout mAccountList;

    public AccountManagerFragment() {
        super(R.layout.fragment_account_manager);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mCurrentName = view.findViewById(R.id.novacraft_current_account_name);
        mCurrentType = view.findViewById(R.id.novacraft_current_account_type);
        mAccountCount = view.findViewById(R.id.novacraft_account_count);
        mEmptyState = view.findViewById(R.id.novacraft_accounts_empty);
        mAccountList = view.findViewById(R.id.novacraft_accounts_list);

        view.findViewById(R.id.novacraft_add_account_button).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), SelectAuthFragment.class, SelectAuthFragment.TAG, null));

        refreshAccountList();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAccountList();
    }

    private void refreshAccountList() {
        if (mAccountList == null || !isAdded()) return;

        mAccountList.removeAllViews();

        String currentName = PojavProfile.getCurrentProfileName(requireContext());
        MinecraftAccount currentAccount = currentName.isEmpty() ? null : MinecraftAccount.load(currentName);

        if (currentAccount == null) {
            mCurrentName.setText(R.string.novacraft_no_active_account);
            mCurrentType.setText(R.string.novacraft_account_choose_hint);
        } else {
            mCurrentName.setText(currentAccount.username);
            mCurrentType.setText(getAccountType(currentAccount));
        }

        List<MinecraftAccount> accounts = PojavProfile.getAllProfiles();
        mAccountCount.setText(getString(R.string.novacraft_accounts_count, accounts.size()));
        mEmptyState.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (MinecraftAccount account : accounts) {
            if (account == null || account.username == null || account.username.isEmpty()) continue;

            View row = inflater.inflate(R.layout.item_novacraft_account, mAccountList, false);
            TextView name = row.findViewById(R.id.novacraft_account_item_name);
            TextView type = row.findViewById(R.id.novacraft_account_item_type);
            Button select = row.findViewById(R.id.novacraft_account_select_button);
            Button remove = row.findViewById(R.id.novacraft_account_remove_button);

            boolean isCurrent = account.username.equals(currentName);
            name.setText(account.username);
            type.setText(getAccountType(account));
            select.setText(isCurrent
                    ? R.string.novacraft_account_current_refresh
                    : R.string.novacraft_account_use);

            select.setOnClickListener(v -> selectAccount(account.username));
            remove.setOnClickListener(v -> confirmRemoveAccount(account.username));
            mAccountList.addView(row);
        }
    }

    private int getAccountType(MinecraftAccount account) {
        if (account.isMicrosoft) return R.string.novacraft_account_type_microsoft;
        if (account.isLocal()) return R.string.novacraft_account_type_offline;
        return R.string.novacraft_account_type_other;
    }

    private void selectAccount(String username) {
        mcAccountSpinner spinner = requireActivity().findViewById(R.id.account_spinner);
        boolean selected = spinner != null && spinner.selectAccount(username);
        if (!selected) {
            PojavProfile.setCurrentProfile(requireContext(), username);
        }
        Tools.backToMainMenu(requireActivity());
    }

    private void confirmRemoveAccount(String username) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.novacraft_account_remove_title)
                .setMessage(getString(R.string.novacraft_account_remove_message, username))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_delete, (dialog, which) -> removeAccount(username))
                .show();
    }

    private void removeAccount(String username) {
        mcAccountSpinner spinner = requireActivity().findViewById(R.id.account_spinner);
        boolean removed = spinner != null && spinner.removeAccountByName(username);
        Toast.makeText(requireContext(),
                removed ? R.string.novacraft_account_removed : R.string.novacraft_account_remove_failed,
                Toast.LENGTH_SHORT).show();
        refreshAccountList();
    }
}
