package com.example.flutterdynamic;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // 启动Flutter的按钮
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DynamicFlutterActivity.class);
                startActivity(intent);
            }
        });
        
        // 添加动态SO测试按钮
        addDynamicSoTestButton();
    }
    
    private void addDynamicSoTestButton() {
        // 获取主布局 - 使用ConstraintLayout
        androidx.constraintlayout.widget.ConstraintLayout mainLayout = findViewById(R.id.main);
        if (mainLayout != null) {
            // 创建测试按钮
            Button testButton = new Button(this);
            testButton.setText("动态SO测试工具");
            testButton.setId(View.generateViewId());
            testButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, DynamicSoTestActivity.class);
                    startActivity(intent);
                }
            });
            
            // 设置ConstraintLayout参数
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
                );
            
            // 设置约束 - 在第一个按钮下方
            params.topToBottom = R.id.button;
            params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            params.setMargins(0, 32, 0, 0);
            
            testButton.setLayoutParams(params);
            mainLayout.addView(testButton);
        }
    }
}