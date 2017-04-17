package wwckl.projectmiki.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.math.BigDecimal;

import wwckl.projectmiki.R;
import wwckl.projectmiki.activity.BillSplitterActivity;
import wwckl.projectmiki.models.BillSplit;

/**
 * Created by Aryn on 7/3/15.
 */
public class SummaryFragment extends Fragment {

    private View mView;
    private LinearLayout mLinearLayout;
    private int mNumOfTextView = 0;
    private int mNumOfPplTreating = 0; // 0 value to indicate there is no active treat split type
    private BigDecimal mTreatAmountPax = BigDecimal.ZERO;
    private int mTotalNumOfPpl = 0;
    private BillSplitterActivity mBillSplitterActivity;
    private String[] mAlphabets;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Get alphabets array
        mAlphabets = getResources().getStringArray(R.array.alphabets);

        // Inflate the layout for this fragment
        mView = inflater.inflate(R.layout.fragment_summary, container, false);
        mLinearLayout = (LinearLayout) mView.findViewById(R.id.layoutSummaryFragment);
        mBillSplitterActivity = (BillSplitterActivity) getActivity();
        return mView;
    }

    public int getRemainingNumOfPplTreating () { return mNumOfPplTreating; }

    public BigDecimal getTreatAmountPax() {
        Log.d ("getTreatAmountPax", mTreatAmountPax.toString());
        return mTreatAmountPax;
    }

    // ********************* BILL SPLIT STATE FLOW ******************************
    // Rules :
    // # each step will only calculate forward, i.e.
    // # a) Once the treat amount has been set, all remaining split type will get
    //      a portion of the treat amount, calculated dynamically.
    // # b) Dutch can be followed by share followed by Dutch/Share for any of the
    //      remaining items. same for Treat-Dutch or Treat-Share
    // # Done option for Treat types will operate differently from Dutch/Share
    //   types, i.e.
    // # a) Done for Treat type will mean Done Treating
    // # b) Done for Dutch/Share type will mean Done Splitting bill.
    // **************************************************************************

    // User clicked next or done, create new BillSplit ListItem
    public BillSplit nextBillSplit (BillSplit.BillSplitType splitType, BigDecimal amount, int pax) {
        BillSplit newBillSplit = null;

        if(isZero(amount) && isZero(mTreatAmountPax))
            return newBillSplit;

        switch (splitType) {
            case TREAT_DUTCH_TYPE:
            case DUTCH_TYPE:
                if (mNumOfPplTreating > 0) {
                    // set billsplit type to TREAT_SHARE_TYPE
                    splitType = BillSplit.BillSplitType.TREAT_DUTCH_TYPE;
                    // set New share amount
                    amount = amount.add(mTreatAmountPax);
                    mNumOfPplTreating--;
                }
                // else, no change needed.
                mTotalNumOfPpl++;
                break;

            case TREAT_SHARE_TYPE:
            case SHARE_TYPE:
                if (pax < 2)
                    return newBillSplit;

                if (mNumOfPplTreating >= pax) {
                    // set billsplit type to TREAT_SHARE_TYPE
                    splitType = BillSplit.BillSplitType.TREAT_SHARE_TYPE;
                    // set New share amount
                    amount = amount.add(mTreatAmountPax.multiply(BigDecimal.valueOf(mNumOfPplTreating)));
                    mNumOfPplTreating = mNumOfPplTreating - pax;
                }
                else if (mNumOfPplTreating != 0) {
                    // set billsplit type to TREAT_SHARE_TYPE
                    splitType = BillSplit.BillSplitType.TREAT_SHARE_TYPE;
                    // share pax > mNumOfPplTreating, calculate new amount
                    amount = amount.add(mTreatAmountPax.multiply(BigDecimal.valueOf(mNumOfPplTreating)));
                    mNumOfPplTreating = 0;
                }
                // else, no change needed.
                mTotalNumOfPpl += pax;
                break;

            case TREAT_TYPE:
                if (pax < 2)
                    return newBillSplit;

                // set number of people treating
                if(mNumOfPplTreating <= 0)
                    mNumOfPplTreating = pax;
                else // already existing treat type
                    return newBillSplit;

                // set amount of treat per person
                mTreatAmountPax = amount.divide(BigDecimal.valueOf(pax), 2, BigDecimal.ROUND_HALF_EVEN);
                mTotalNumOfPpl ++;
                break;
        }

        // Create new bill split with updated values
        newBillSplit = new BillSplit(splitType, amount, pax);

        // Add summary text of new bill split to fragment
        addSummaryText(newBillSplit);
        // check if we can clear Treat amount per person.
        // Do this only after we have added Summary Text
        if(mNumOfPplTreating == 0)
            mTreatAmountPax = BigDecimal.ZERO;

        // return new billSplit to be added to Bill.BillSplit array
        return newBillSplit;
    }

    // User clicked prev, need to retrieve previous items and
    // delete last BillSplit item.
    public void prevBillSplit (BillSplit lastBillSplit) {
        if (lastBillSplit == null)
            return;

        mNumOfTextView--;
        mLinearLayout.removeViewAt(mNumOfTextView);
        switch (lastBillSplit.getSplitType()) {
            case TREAT_DUTCH_TYPE:
                mNumOfPplTreating++;
            case DUTCH_TYPE:
                mTotalNumOfPpl--;
                break;

            case TREAT_SHARE_TYPE:
                mNumOfPplTreating = mNumOfPplTreating - lastBillSplit.getNoOfPplSharing();
            case SHARE_TYPE:
                mTotalNumOfPpl = mTotalNumOfPpl - lastBillSplit.getNoOfPplSharing();
                break;

            case TREAT_TYPE:
                mNumOfPplTreating = 0;
                mTreatAmountPax = BigDecimal.ZERO;
                mTotalNumOfPpl--;
                break;
        }
    }

    private void addSummaryText (BillSplit billSplit) {
        String summaryText = "";
        String treatTypeText = getBillSplitString(billSplit.getSplitType());
        BigDecimal amount = billSplit.getTotalAmount();
        TextView summaryTextView = new TextView(getActivity());

        // Build SummaryText
        switch (billSplit.getSplitType()) {
            case TREAT_DUTCH_TYPE:
                summaryText = "T+";
                //summaryText = getBillSplitString(BillSplit.BillSplitType.TREAT_TYPE) + " ($" + mTreatAmountPax.toString() + ") ";
            case DUTCH_TYPE:
                summaryText = summaryText + treatTypeText + " \t" + getString(R.string.guest) + " " +
                        Array.get(mAlphabets, mTotalNumOfPpl-1) + " : $" + amount.toString();
                summaryTextView.setGravity(Gravity.RIGHT);
                break;

            case TREAT_TYPE:
                summaryText = treatTypeText + " ($" + amount.toString() + ") by " +
                        billSplit.getNoOfPplSharing() + " : $" + billSplit.getSplitAmount().toString();
                summaryTextView.setGravity(Gravity.LEFT);
                break;

            case TREAT_SHARE_TYPE:
                summaryText = "T+";
                //summaryText = getBillSplitString(BillSplit.BillSplitType.TREAT_TYPE) + " ($" + mTreatAmountPax.toString() + ") ";
            case SHARE_TYPE:
                summaryText = summaryText + treatTypeText + " ($" + amount.toString() + ") by " +
                        billSplit.getNoOfPplSharing() + " : $" + billSplit.getSplitAmount().toString();
                summaryTextView.setGravity(Gravity.RIGHT);
                break;
        }

        summaryTextView.setId(mNumOfTextView); //Set id to remove in the future.
        summaryTextView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        summaryTextView.setText(summaryText);
        summaryTextView.setClickable(true);
        summaryTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if( v.isSelected() ) {
                    v.setSelected(false);
                    v.setBackgroundColor(getResources().getColor(R.color.background_material_light));
                    mBillSplitterActivity.highlightSplitItems(v.getId(), false);
                }
                else {
                    v.setSelected(true);
                    v.setBackgroundColor(getResources().getColor(R.color.background));
                    mBillSplitterActivity.highlightSplitItems(v.getId(), true);
                }
                Log.d("onClick tv", Integer.toString(v.getId()));
            }
        });

        try{
            mLinearLayout.addView(summaryTextView);
            mNumOfTextView++;
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public Boolean isZero(BigDecimal bdAmount){
        if(bdAmount.compareTo(BigDecimal.ZERO) == 0)
            return  true;
        return false;
    }

    public String getBillSplitString(BillSplit.BillSplitType splitType){
        switch (splitType) {
            case DUTCH_TYPE:
            case TREAT_DUTCH_TYPE:
                return getString(R.string.dutch).toUpperCase();
            case SHARE_TYPE:
            case TREAT_SHARE_TYPE:
                return getString(R.string.share).toUpperCase();
            case TREAT_TYPE:
                return getString(R.string.treat).toUpperCase();
            default:
                return getString(R.string.unknown_type);
        }
    }

    public void smallView() {
        TextView textView;
        for (int i = 0; i < mNumOfTextView; i++) {
            textView = (TextView) mLinearLayout.findViewById(i);
            textView.setTextAppearance(this.getActivity(), android.R.style.TextAppearance_Medium);
        }
    }

    public void largeView() {
        TextView textView;
        for (int i = 0; i < mNumOfTextView; i++) {
            textView = (TextView) mLinearLayout.findViewById(i);
            textView.setTextAppearance(this.getActivity(), android.R.style.TextAppearance_Large);
        }

    }
}
