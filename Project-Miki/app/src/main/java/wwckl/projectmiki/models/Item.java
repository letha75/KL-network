package wwckl.projectmiki.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Created by cheeyim on 2/2/2015.
 */
public class Item implements Parcelable {
    public final int fNOT_SELECTED = -1;

    private String description;
    private BigDecimal price;
    private int guestIndex;

    public Item(String description, BigDecimal price){
        this.description = description;
        this.price = price;
        this.guestIndex = fNOT_SELECTED; // to indicate not selected
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getGuestIndex() { return guestIndex; }

    public void setGuestIndex(int guestIndex) { this.guestIndex = guestIndex; }

    // ******************* PARCELABLE IMPLEMENTATION *******************
    public static final Parcelable.Creator<Item> CREATOR
            = new Parcelable.Creator<Item>() {

        // This simply calls our new constructor (typically private) and
        // passes along the unmarshalled `Parcel`, and then returns the new object!
        @Override
        public Item createFromParcel(Parcel in) {
            return new Item(in);
        }

        // We just need to copy this and change the type to match our class.
        @Override
        public Item[] newArray(int size) {
            return new Item[size];
        }
    };

    public int describeContents() {
        return 0;
    }

    private Item(Parcel in) { readFromParcel(in); }

    // parcel read and write must be in the exact same order
    // Assumes new Edit Activity, therefore, mNoOfPplSharing and BillSplitStack is ignored for Parcel.
    public void readFromParcel(Parcel in) {
        description = in.readString();
        price = new BigDecimal(in.readString());
        guestIndex = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(description);
        out.writeString(price.toString());
        out.writeInt(guestIndex);
    }
    // ***************** END PARCELABLE IMPLEMENTATION *******************
}
