package unimelb.comp90018.equaltrip;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
public class CurrencyUtils {
    public static String format(double amount, String currencyCode) {
        try {
            NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.getDefault());
            if (currencyCode != null && !currencyCode.isEmpty())
                nf.setCurrency(Currency.getInstance(currencyCode));
            return nf.format(amount);
        } catch (Exception e) {
            // 回退
            return String.format(Locale.getDefault(), "%.2f", amount);
        }
    }
}
