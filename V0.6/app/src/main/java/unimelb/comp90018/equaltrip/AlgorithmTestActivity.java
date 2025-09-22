package unimelb.comp90018.equaltrip;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class AlgorithmTestActivity extends AppCompatActivity {
    // 记录最近弹过的对话框（key -> 上次时间），只在主线程用即可
    private final java.util.Map<String, Long> recentDialogs = new java.util.HashMap<>();
    private static final long DEDUPE_WINDOW_MS = 2000; // 2s 窗口

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_algorithm_test);

        // 处理系统栏内边距（保留你原本的代码）
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ---- 1) 找到布局里的控件
        AutoCompleteTextView payerDropdown = findViewById(R.id.payerDropdown);
        ChipGroup participantsGroup = findViewById(R.id.participantsGroup);
        TextInputLayout tilPayer = findViewById(R.id.til_payer);
        TextInputLayout tilAmount = findViewById(R.id.til_amount);
        TextInputEditText amountEdit = findViewById(R.id.amountEdit);
        MaterialButton btnDone = findViewById(R.id.btnDone);

        // ---- 2) 人员数据
        // 方案A：直接在代码里写死（最快）
        String[] people = new String[]{"Alice", "Bob", "Charlie", "Diana", "Eva"};

        // （可选）方案B：如果你建了 res/values/arrays.xml:
        // String[] people = getResources().getStringArray(R.array.people);

        // ---- 3) 支付方（单选下拉）
        ArrayAdapter<String> payerAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, people);
        payerDropdown.setAdapter(payerAdapter);
        // 不允许手动输入，只能点选
        payerDropdown.setKeyListener(null);

        // ---- 4) 参与方（多选 Chip）
        for (String p : people) {
            Chip chip = new Chip(this);
            chip.setText(p);
            chip.setCheckable(true); // 可勾选
            chip.setClickable(true);
            participantsGroup.addView(chip);
        }
        // ChipGroup 已在 XML 里设置 app:singleSelection="false"，天然多选，不用再额外处理

        // ---- 5) 完成按钮：读取选择 + 简单校验
        btnDone.setOnClickListener(v -> {
            btnDone.setEnabled(false); // ⛔ 禁用

            try{
                String payer = payerDropdown.getText() == null ? "" : payerDropdown.getText().toString().trim();
                String amountStr = amountEdit.getText() == null ? "" : amountEdit.getText().toString().trim();

                // 读取多选参与方
                List<Integer> checkedIds = participantsGroup.getCheckedChipIds();
                List<String> participants = new ArrayList<>();
                for (int id : checkedIds) {
                    Chip c = participantsGroup.findViewById(id);
                    if (c != null) participants.add(c.getText().toString());
                }

                // 表单校验
                if (payer.isEmpty()) {
                    tilPayer.setError("Please select a payer");
                    return;
                } else {
                    tilPayer.setError(null);
                }
                if (participants.isEmpty()) {
                    Toast.makeText(this, "Please select at least one participant", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (participants.size() == 1 && participants.get(0).equals(payer)) {
                    Toast.makeText(this, "Payer cannot pay only for themselves.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (amountStr.isEmpty()) {
                    tilAmount.setError("Please enter amount");
                    return;
                } else {
                    tilAmount.setError(null);
                }

                if (!amountStr.matches("^\\d+(\\.\\d{1,2})?$")) {
                    tilAmount.setError("Amount can have at most 2 decimal places");
                    return;
                }

                // ---- 6) 计算并弹窗结果 ----
                try {
                    java.math.BigDecimal amount = new java.math.BigDecimal(amountStr.trim());

                    if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                        tilAmount.setError("Amount must be > 0");
                        return;
                    } else {
                        tilAmount.setError(null);
                    }

                    // 参与方人数数量作为分母
                    List<String> lines = calculateSplits(payer, participants, amount);

                    if (lines.isEmpty()) {
                        Toast.makeText(this, "No debts generated.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 弹窗展示结果
                    showResultDialog(String.join("\n", lines));

                } catch (NumberFormatException nfe) {
                    tilAmount.setError("Invalid number");
                }

        }finally{
            btnDone.setEnabled(true); // ✅ 恢复
        }
        });
    }

    private List<String> calculateSplits(String payer,
                                                   List<String> participants,
                                                   java.math.BigDecimal amount) {
        // ---- 基本校验 ---
        // 去重并保持顺序，清除空白名
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>();
        for (String p : participants) {
            if (p != null) {
                String t = p.trim();
                if (!t.isEmpty()) uniq.add(t);
            }
        }
        if (uniq.isEmpty()) return new ArrayList<>();


        int headcount = uniq.size();               // 受益总人数

        // 欠款人列表：受益人里排除支付方（支付方自己不生成欠款）
        List<String> debtors = new ArrayList<>();
        for (String p : uniq) {
            if (!p.equals(payer)) debtors.add(p);
        }

        // 每位受益人的“应承担原始份额”（高精度，后面到分再四舍五入）
        java.math.BigDecimal rawShare = amount
                .divide(new java.math.BigDecimal(headcount), 6, java.math.RoundingMode.HALF_UP);

        // 先给每个欠款人分配四舍五入到分的份额
        List<java.math.BigDecimal> shares = new ArrayList<>();
        java.math.BigDecimal sumRounded = java.math.BigDecimal.ZERO;
        for (int i = 0; i < debtors.size(); i++) {
            java.math.BigDecimal s = rawShare.setScale(2, java.math.RoundingMode.HALF_UP);
            shares.add(s);
            sumRounded = sumRounded.add(s);
        }

        // 非支付方合计应付 = amount * (欠款人数 / 受益人数)  —— 高精度，不预四舍五入
        java.math.BigDecimal expectedNonPayerTotalHi = amount
                .multiply(new java.math.BigDecimal(debtors.size()))
                .divide(new java.math.BigDecimal(headcount), 6, java.math.RoundingMode.HALF_UP);

        // 误差（以“分”为单位），正=需要再加的分，负=需要减的分
        int cents = expectedNonPayerTotalHi
                .subtract(sumRounded)
                .movePointRight(2)                      // 元 -> 分
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValue();

        // 逐分校正，保证合计精确等于应付总额
        int idx = 0;
        while (cents != 0 && !shares.isEmpty()) {
            java.math.BigDecimal delta = (cents > 0)
                    ? new java.math.BigDecimal("0.01")
                    : new java.math.BigDecimal("-0.01");
            java.math.BigDecimal newVal = shares.get(idx).add(delta);

            // 不允许负数
            if (newVal.compareTo(java.math.BigDecimal.ZERO) >= 0) {
                shares.set(idx, newVal);
                cents += (cents > 0 ? -1 : 1);
            }
            idx = (idx + 1) % shares.size();
        }

        // 生成输出文案
        java.text.DecimalFormat df = new java.text.DecimalFormat("0.00");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < debtors.size(); i++) {
            java.math.BigDecimal s = shares.get(i);
            if (s.compareTo(java.math.BigDecimal.ZERO) > 0) {
                result.add(debtors.get(i) + " owes " + payer + " " + df.format(s));
            }
        }
        return result;
    }

    /** 简单弹窗显示计算结果 */
    /** 兼容旧用法：把 message 当作 key 使用（最少改动） */
    private void showResultDialog(String message) {
        showResultDialog(/*key*/ message, message);
    }

    /** 新方法：相同 key 在 2 秒内只弹一次 */
    private void showResultDialog(String key, String message) {
        long now = android.os.SystemClock.uptimeMillis();
        Long last = recentDialogs.get(key);
        if (last != null && (now - last) < DEDUPE_WINDOW_MS) {
            return; // 丢弃重复
        }
        recentDialogs.put(key, now);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Split Result")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();

        // 过了窗口自动清理，避免 Map 膨胀
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> recentDialogs.remove(key), DEDUPE_WINDOW_MS);
    }

}
