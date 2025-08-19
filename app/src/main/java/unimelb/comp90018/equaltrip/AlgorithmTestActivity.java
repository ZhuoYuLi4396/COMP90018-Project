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
            String payer = payerDropdown.getText() == null ? "" : payerDropdown.getText().toString().trim();
            String amountStr = amountEdit.getText() == null ? "" : amountEdit.getText().toString().trim();

            // 读取多选参与方
            List<Integer> checkedIds = participantsGroup.getCheckedChipIds();
            List<String> participants = new ArrayList<>();
            for (int id : checkedIds) {
                Chip c = participantsGroup.findViewById(id);
                if (c != null) participants.add(c.getText().toString());
            }

            // 不允许支付方也在参与方里（可按需要保留/去掉）
            participants.remove(payer);

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
            if (amountStr.isEmpty()) {
                tilAmount.setError("Please enter amount");
                return;
            } else {
                tilAmount.setError(null);
            }

            // TODO: 把 payer / participants / amountStr 交给你的算法
            Toast.makeText(this,
                    "Payer: " + payer + "\nParticipants: " + participants + "\nAmount: " + amountStr,
                    Toast.LENGTH_LONG).show();
        });
    }
}
