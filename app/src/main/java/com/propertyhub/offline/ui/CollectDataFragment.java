package com.propertyhub.offline.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.propertyhub.offline.R;
import com.propertyhub.offline.model.PropertyCategory;

public class CollectDataFragment extends Fragment {

    private static final String STATE_SELECTED_PAGE = "state_selected_category_page";
    private static final int PAGE_HOUSE = 0;
    private static final int PAGE_LAND = 1;

    private TabLayout categoryTabLayout;
    private ViewPager2 categoryViewPager;
    private CategoryPagerAdapter pagerAdapter;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_collect_data, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        categoryTabLayout = view.findViewById(R.id.categoryTabLayout);
        categoryViewPager = view.findViewById(R.id.categoryViewPager);

        pagerAdapter = new CategoryPagerAdapter(this);
        categoryViewPager.setAdapter(pagerAdapter);
        categoryViewPager.setOffscreenPageLimit(1);

        new TabLayoutMediator(categoryTabLayout, categoryViewPager, (tab, position) -> {
            if (position == PAGE_HOUSE) {
                tab.setText(R.string.tab_house_for_rent);
            } else {
                tab.setText(R.string.tab_land_for_sale);
            }
        }).attach();

        categoryViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                applyTabThemeColor(position);
            }
        });

        int restoredPage = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_SELECTED_PAGE, PAGE_HOUSE)
                : PAGE_HOUSE;
        categoryViewPager.setCurrentItem(restoredPage, false);
        applyTabThemeColor(restoredPage);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (categoryViewPager != null) {
            outState.putInt(STATE_SELECTED_PAGE, categoryViewPager.getCurrentItem());
        }
    }

    public void retryPendingSubmission(@NonNull PropertyCategory category) {
        if (!isAdded() || categoryViewPager == null) {
            return;
        }
        int page = category == PropertyCategory.HOUSE_FOR_RENT ? PAGE_HOUSE : PAGE_LAND;
        categoryViewPager.setCurrentItem(page, false);
        categoryViewPager.post(() -> {
            if (!isAdded()) {
                return;
            }
            Fragment pageFrag = getChildFragmentManager().findFragmentByTag("f" + page);
            if (pageFrag instanceof CategoryFormPageFragment) {
                ((CategoryFormPageFragment) pageFrag).triggerSubmit();
            }
        });
    }

    public void refreshFormsPreservingDrafts() {
        if (!isAdded()) {
            return;
        }
        for (Fragment child : getChildFragmentManager().getFragments()) {
            if (child instanceof CategoryFormPageFragment) {
                ((CategoryFormPageFragment) child).refreshFieldsPreservingDrafts();
            }
        }
    }

    private void applyTabThemeColor(int position) {
        if (!isAdded() || categoryTabLayout == null) {
            return;
        }
        int activeColor = ContextCompat.getColor(
                requireContext(),
                position == PAGE_HOUSE ? R.color.blue_primary : R.color.green_primary
        );
        int inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_hint);
        categoryTabLayout.setSelectedTabIndicatorColor(activeColor);
        categoryTabLayout.setTabTextColors(inactiveColor, activeColor);
    }

    private static final class CategoryPagerAdapter extends FragmentStateAdapter {

        CategoryPagerAdapter(@NonNull Fragment hostFragment) {
            super(hostFragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return CategoryFormPageFragment.newInstance(
                    position == PAGE_HOUSE
                            ? PropertyCategory.HOUSE_FOR_RENT
                            : PropertyCategory.LAND_FOR_SALE
            );
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
