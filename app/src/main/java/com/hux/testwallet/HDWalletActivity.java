package com.hux.testwallet;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liar.testwallet.R;

import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.WalletFile;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HD 钱包管理界面
 */
public class HDWalletActivity extends AppCompatActivity {
    private static final String TAG = "HDWalletActivity";

    private Bip39WalletManager bip39Manager;
    private List<String> currentMnemonics;
    private List<WalletFile> derivedWallets = new ArrayList<>();
    private int currentAccountIndex = 0;

    // UI 组件
    private TextView networkTitleText;
    private Button btnGenerateWallet;
    private Button btnRecoverWallet;
    private LinearLayout mnemonicLayout;
    private TextView mnemonicText;
    private Button btnCopyMnemonic;
    private LinearLayout recoverLayout;
    private TextView mnemonicInput;
    private Button btnConfirmRecover;
    private TextView currentAddressText;
    private TextView currentBalanceText;
    private Button btnRefreshBalance;
    private Button btnDeriveAddress;
    private TextView derivedAddressesLabel;
    private LinearLayout derivedAddressesContainer;
    private Button btnShowKeystore;
    private Button btnShowPrivateKey;
    private TextView keystoreText;
    private TextView privateKeyText;

    private Web3j web3j;
    private WalletFile currentWallet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hd_wallet);

        initViews();
        initData();
        new android.os.Handler().postDelayed(() -> {
            checkExistingWallet();
        }, 500);    }

    private void initViews() {
        networkTitleText = findViewById(R.id.network_title);
        btnGenerateWallet = findViewById(R.id.btn_generate_wallet);
        btnRecoverWallet = findViewById(R.id.btn_recover_wallet);
        mnemonicLayout = findViewById(R.id.mnemonic_layout);
        mnemonicText = findViewById(R.id.mnemonic_text);
        btnCopyMnemonic = findViewById(R.id.btn_copy_mnemonic);
        recoverLayout = findViewById(R.id.recover_layout);
        mnemonicInput = findViewById(R.id.mnemonic_input);
        btnConfirmRecover = findViewById(R.id.btn_confirm_recover);
        currentAddressText = findViewById(R.id.current_address);
        currentBalanceText = findViewById(R.id.current_balance);
        btnRefreshBalance = findViewById(R.id.btn_refresh_balance);
        btnDeriveAddress = findViewById(R.id.btn_derive_address);
        derivedAddressesLabel = findViewById(R.id.derived_addresses_label);
        derivedAddressesContainer = findViewById(R.id.derived_addresses_container);
        btnShowKeystore = findViewById(R.id.btn_show_keystore);
        btnShowPrivateKey = findViewById(R.id.btn_show_private_key);
        keystoreText = findViewById(R.id.keystore_text);
        privateKeyText = findViewById(R.id.private_key_text);

        setupClickListeners();
    }

    private void initData() {
        bip39Manager = Bip39WalletManager.getInstance();
        web3j = Web3j.build(new HttpService(Constants.ETHEREUM_SEPOLIA_URL));
    }

    private void setupClickListeners() {
        btnGenerateWallet.setOnClickListener(v -> generateNewWallet());
        btnRecoverWallet.setOnClickListener(v -> showRecoverLayout());
        btnCopyMnemonic.setOnClickListener(v -> copyMnemonicToClipboard());
        btnConfirmRecover.setOnClickListener(v -> recoverWalletFromMnemonic());
        btnRefreshBalance.setOnClickListener(v -> refreshCurrentBalance());
        btnDeriveAddress.setOnClickListener(v -> deriveNewAddress());
        btnShowKeystore.setOnClickListener(v -> toggleKeystoreDisplay());
        btnShowPrivateKey.setOnClickListener(v -> togglePrivateKeyDisplay());
    }

    private void checkExistingWallet() {
        loadExistingWallet();
    }

    private void generateNewWallet() {
        AsyncTask.execute(() -> {
            try {
                // 生成助记词
                currentMnemonics = bip39Manager.generateMnemonic();

                if (currentMnemonics != null && !currentMnemonics.isEmpty()) {
                    // 从助记词创建主钱包
                    WalletFile walletFile = bip39Manager.createWalletFromMnemonic(
                            currentMnemonics, Constants.PASSWORD);

                    if (walletFile != null) {
                        currentWallet = walletFile;
                        derivedWallets.clear();
                        derivedWallets.add(walletFile);
                        currentAccountIndex = 0;

                        // 保存钱包文件
                        saveWalletFile(walletFile);
                        saveDerivedWallets();

                        runOnUiThread(() -> {
                            showMnemonicBackupDialog(currentMnemonics);
                            updateWalletUI();
                            hideRecoverLayout();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Generate wallet failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "创建钱包失败", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showRecoverLayout() {
        recoverLayout.setVisibility(View.VISIBLE);
        mnemonicLayout.setVisibility(View.GONE);
    }

    private void hideRecoverLayout() {
        recoverLayout.setVisibility(View.GONE);
    }

    private void recoverWalletFromMnemonic() {
        String mnemonicPhrase = mnemonicInput.getText().toString().trim();

        if (TextUtils.isEmpty(mnemonicPhrase)) {
            Toast.makeText(this, "请输入助记词", Toast.LENGTH_SHORT).show();
            return;
        }

        AsyncTask.execute(() -> {
            WalletFile walletFile = bip39Manager.recoverWallet(mnemonicPhrase, Constants.PASSWORD);
            if (walletFile != null) {
                currentWallet = walletFile;
                derivedWallets.clear();
                derivedWallets.add(walletFile);
                currentAccountIndex = 0;

                saveWalletFile(walletFile);
                saveDerivedWallets();

                runOnUiThread(() -> {
                    updateWalletUI();
                    hideRecoverLayout();
                    Toast.makeText(this, "钱包恢复成功", Toast.LENGTH_SHORT).show();
                });
            } else {
                runOnUiThread(() ->
                        Toast.makeText(this, "助记词无效", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deriveNewAddress() {
        if (currentMnemonics == null || currentMnemonics.isEmpty()) {
            Toast.makeText(this, "请先创建或恢复钱包", Toast.LENGTH_SHORT).show();
            return;
        }

        AsyncTask.execute(() -> {
            try {
                currentAccountIndex++;
                WalletFile newWallet = bip39Manager.deriveNewAddress(
                        currentMnemonics, Constants.PASSWORD, currentAccountIndex);

                if (newWallet != null) {
                    derivedWallets.add(newWallet);
                    // 保存新的派生钱包列表
                    saveDerivedWallets();
                    saveCurrentAccountIndex();
                    runOnUiThread(() -> {
                        updateDerivedAddressesUI();
                        Toast.makeText(this,
                                "已派生新地址 #" + currentAccountIndex,
                                Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Derive new address failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "派生地址失败", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateWalletUI() {
        if (currentWallet != null) {
            String address = Constants.HEX_PREFIX + currentWallet.getAddress();
            currentAddressText.setText(address);
            refreshCurrentBalance();
            updateDerivedAddressesUI();
        }
    }

    private void refreshCurrentBalance() {
        if (currentWallet == null) return;

        AsyncTask.execute(() -> {
            try {
                String address = Constants.HEX_PREFIX + currentWallet.getAddress();
                BigInteger balance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                        .send().getBalance();

                runOnUiThread(() -> {
                    BigDecimal ethBalance = Convert.fromWei(balance.toString(), Convert.Unit.ETHER);
                    String balanceString = ethBalance.setScale(6, RoundingMode.FLOOR).toPlainString() + " ETH";
                    currentBalanceText.setText(balanceString);
                });
            } catch (IOException e) {
                Log.e(TAG, "Get balance failed", e);
                runOnUiThread(() ->
                        currentBalanceText.setText("查询失败"));
            }
        });
    }

    private void updateDerivedAddressesUI() {
        derivedAddressesContainer.removeAllViews();

        if (derivedWallets.isEmpty()) {
            derivedAddressesLabel.setVisibility(View.VISIBLE);
            derivedAddressesContainer.setVisibility(View.GONE);
            return;
        }

        derivedAddressesLabel.setVisibility(View.GONE);
        derivedAddressesContainer.setVisibility(View.VISIBLE);

        for (int i = 0; i < derivedWallets.size(); i++) {
            WalletFile wallet = derivedWallets.get(i);
            View addressView = createAddressView(wallet, i);
            derivedAddressesContainer.addView(addressView);
        }
    }

    private View createAddressView(WalletFile wallet, int index) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(8, 8, 8, 8);
        layout.setBackgroundResource(R.drawable.edittext_background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 8;
        layout.setLayoutParams(params);

        TextView textView = new TextView(this);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        textView.setTextSize(12);
        textView.setText(String.format("地址 #%d: %s", index,
                Constants.HEX_PREFIX + wallet.getAddress()));

        Button selectBtn = new Button(this);
        selectBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        selectBtn.setText("选择");
        selectBtn.setBackgroundResource(R.drawable.button_small);
        selectBtn.setTextColor(getResources().getColor(android.R.color.white));
        selectBtn.setOnClickListener(v -> selectWallet(wallet, index));

        layout.addView(textView);
        layout.addView(selectBtn);

        return layout;
    }

    private void selectWallet(WalletFile wallet, int index) {
        currentWallet = wallet;
        currentAccountIndex = index;
        updateWalletUI();
        Toast.makeText(this, "已切换到地址 #" + index, Toast.LENGTH_SHORT).show();
    }

    private void toggleKeystoreDisplay() {
        if (currentWallet == null) {
            Toast.makeText(this, "请先创建钱包", Toast.LENGTH_SHORT).show();
            return;
        }

        if (keystoreText.getVisibility() == View.VISIBLE) {
            keystoreText.setVisibility(View.GONE);
            btnShowKeystore.setText("显示KeyStore");
        } else {
            String keystore = EthWalletController.getInstance().exportKeyStore(currentWallet);
            keystoreText.setText(keystore);
            keystoreText.setVisibility(View.VISIBLE);
            privateKeyText.setVisibility(View.GONE);
            btnShowKeystore.setText("隐藏KeyStore");
            btnShowPrivateKey.setText("显示私钥");
        }
    }

    private void togglePrivateKeyDisplay() {
        if (currentWallet == null) {
            Toast.makeText(this, "请先创建钱包", Toast.LENGTH_SHORT).show();
            return;
        }

        if (privateKeyText.getVisibility() == View.VISIBLE) {
            privateKeyText.setVisibility(View.GONE);
            btnShowPrivateKey.setText("显示私钥");
        } else {
            String privateKey = EthWalletController.getInstance().exportPrivateKey(currentWallet);
            privateKeyText.setText(privateKey);
            privateKeyText.setVisibility(View.VISIBLE);
            keystoreText.setVisibility(View.GONE);
            btnShowPrivateKey.setText("隐藏私钥");
            btnShowKeystore.setText("显示KeyStore");
        }
    }

    private void showMnemonicBackupDialog(List<String> mnemonics) {
        StringBuilder mnemonicTextBuilder = new StringBuilder();
        for (int i = 0; i < mnemonics.size(); i++) {
            mnemonicTextBuilder.append(i + 1).append(". ").append(mnemonics.get(i));
            if (i < mnemonics.size() - 1) {
                mnemonicTextBuilder.append(" ");
            }
        }

        mnemonicText.setText(mnemonicTextBuilder.toString());
        mnemonicLayout.setVisibility(View.VISIBLE);

        new AlertDialog.Builder(this)
                .setTitle("⚠️ 重要提示")
                .setMessage("请立即备份以下助记词！\n\n" +
                        "• 按顺序准确抄写12个单词\n" +
                        "• 保存在安全的地方\n" +
                        "• 不要截图或通过网络传输\n" +
                        "• 这是恢复钱包的唯一方式")
                .setPositiveButton("我已备份", (dialog, which) -> {
                    // 用户确认已备份
                })
                .setCancelable(false)
                .show();
    }

    private void copyMnemonicToClipboard() {
        if (currentMnemonics != null && !currentMnemonics.isEmpty()) {
            String mnemonicString = TextUtils.join(" ", currentMnemonics);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("助记词", mnemonicString);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "助记词已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 加载已存在的钱包
     */
    private void loadExistingWallet() {
        AsyncTask.execute(() -> {
            try {
                // 1. 检查是否有加密的助记词
                SharedPreferences prefs = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE);
                String encryptedMnemonic = prefs.getString("encrypted_mnemonic", null);

                if (encryptedMnemonic != null) {
                    // 2. 解密助记词
                    String mnemonicString = new String(
                            Base64.decode(encryptedMnemonic, Base64.DEFAULT),
                            StandardCharsets.UTF_8
                    );

                    currentMnemonics = Arrays.asList(mnemonicString.split(" "));

                    // 3. 加载当前账户索引
                    currentAccountIndex = prefs.getInt("current_account_index", 0);

                    // 4. 重新派生当前钱包
                    WalletFile currentWalletFile = bip39Manager.deriveNewAddress(
                            currentMnemonics, Constants.PASSWORD, currentAccountIndex);

                    if (currentWalletFile != null) {
                        currentWallet = currentWalletFile;

                        // 5. 加载所有派生钱包
                        loadDerivedWallets();

                        runOnUiThread(() -> {
                            updateWalletUI();
                            Toast.makeText(HDWalletActivity.this,
                                    "钱包加载成功", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                }

                // 6. 如果没有助记词，尝试从钱包文件加载
                loadFromWalletFiles();

            } catch (Exception e) {
                Log.e(TAG, "Load existing wallet failed", e);
                runOnUiThread(() ->
                        loadFromWalletFiles() // 回退到文件加载
                );
            }
        });
    }

    /**
     * 从钱包文件加载（兼容旧版本）
     */
    private void loadFromWalletFiles() {
        try {
            File walletDir = getDir("eth2", Context.MODE_PRIVATE);
            if (walletDir.exists() && walletDir.listFiles().length > 0) {
                File[] files = walletDir.listFiles();
                if (files != null && files.length > 0) {
                    // 加载第一个钱包文件
                    ObjectMapper objectMapper = new ObjectMapper();
                    WalletFile walletFile = objectMapper.readValue(files[0], WalletFile.class);

                    currentWallet = walletFile;
                    derivedWallets.clear();
                    derivedWallets.add(walletFile);
                    currentAccountIndex = 0;

                    runOnUiThread(() -> {
                        updateWalletUI();
                        Toast.makeText(this,
                                "检测到旧版钱包，请备份后使用HD钱包功能",
                                Toast.LENGTH_LONG).show();
                    });
                }
            } else {
                runOnUiThread(() ->
                        Log.d(TAG, "No existing wallet found")
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Load from wallet files failed", e);
            runOnUiThread(() ->
                    Toast.makeText(this, "加载钱包失败", Toast.LENGTH_SHORT).show()
            );
        }
    }

    /**
     * 加载所有派生钱包
     */
    private void loadDerivedWallets() {
        try {
            derivedWallets.clear();
            SharedPreferences prefs = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE);

            int walletCount = prefs.getInt("derived_wallet_count", 0);

            if (walletCount > 0 && currentMnemonics != null) {
                for (int i = 0; i <= walletCount; i++) {
                    WalletFile wallet = bip39Manager.deriveNewAddress(
                            currentMnemonics, Constants.PASSWORD, i);
                    if (wallet != null) {
                        derivedWallets.add(wallet);
                    }
                }
                Log.d(TAG, "Loaded " + derivedWallets.size() + " derived wallets");
            } else {
                // 如果没有保存的派生钱包，至少添加当前钱包
                if (currentWallet != null) {
                    derivedWallets.add(currentWallet);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Load derived wallets failed", e);
        }
    }

    /**
     * 从加密存储中读取助记词
     */
    private List<String> loadEncryptedMnemonic() {
        try {
            SharedPreferences prefs = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE);
            String encryptedMnemonic = prefs.getString("encrypted_mnemonic", null);

            if (encryptedMnemonic != null) {
                String mnemonicString = new String(
                        Base64.decode(encryptedMnemonic, Base64.DEFAULT),
                        StandardCharsets.UTF_8
                );
                return Arrays.asList(mnemonicString.split(" "));
            }
        } catch (Exception e) {
            Log.e(TAG, "Load encrypted mnemonic failed", e);
        }
        return null;
    }

    /**
     * 保存钱包文件到本地存储
     */
    private void saveWalletFile(WalletFile walletFile) {
        try {
            // 获取钱包存储目录
            File walletDir = getDir("eth2", Context.MODE_PRIVATE);
            if (!walletDir.exists()) {
                walletDir.mkdirs();
            }

            // 生成钱包文件名
            String walletFileName = getWalletFileName(walletFile);
            File destination = new File(walletDir, walletFileName);

            // 使用 ObjectMapper 写入钱包文件
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writeValue(destination, walletFile);

            // 保存助记词（加密存储）
            if (currentMnemonics != null && !currentMnemonics.isEmpty()) {
                saveEncryptedMnemonic(currentMnemonics);
            }

            // 保存当前账户索引
            saveCurrentAccountIndex();

            Log.d(TAG, "Wallet saved successfully: " + walletFile.getAddress());

        } catch (Exception e) {
            Log.e(TAG, "Save wallet file failed", e);
            runOnUiThread(() ->
                    Toast.makeText(this, "保存钱包失败", Toast.LENGTH_SHORT).show());
        }
    }

    /**
     * 生成钱包文件名（与 EthWalletController 保持一致）
     */
    private String getWalletFileName(WalletFile walletFile) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("'UTC--'yyyy-MM-dd'T'HH-mm-ss.SSS'--'", Locale.US);
        return dateFormat.format(new Date()) + walletFile.getAddress() + ".json";
    }

    /**
     * 加密保存助记词到 SharedPreferences
     */
    private void saveEncryptedMnemonic(List<String> mnemonics) {
        try {
            String mnemonicString = TextUtils.join(" ", mnemonics);

            // 简单的 Base64 编码（实际项目中应该使用更安全的加密方式）
            String encryptedMnemonic = Base64.encodeToString(
                    mnemonicString.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);

            SharedPreferences prefs = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putString("encrypted_mnemonic", encryptedMnemonic)
                    .apply();

            Log.d(TAG, "Mnemonic saved (encrypted)");
        } catch (Exception e) {
            Log.e(TAG, "Save mnemonic failed", e);
        }
    }

    /**
     * 保存当前账户索引
     */
    private void saveCurrentAccountIndex() {
        SharedPreferences prefs = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("current_account_index", currentAccountIndex)
                .apply();
    }

    /**
     * 保存派生钱包列表
     */
    private void saveDerivedWallets() {
        try {
            SharedPreferences prefs = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // 保存派生钱包数量
            editor.putInt("derived_wallet_count", derivedWallets.size());

            // 保存每个派生钱包的地址
            for (int i = 0; i < derivedWallets.size(); i++) {
                WalletFile wallet = derivedWallets.get(i);
                editor.putString("derived_wallet_" + i, wallet.getAddress());
            }

            editor.apply();
            Log.d(TAG, "Derived wallets saved: " + derivedWallets.size());

        } catch (Exception e) {
            Log.e(TAG, "Save derived wallets failed", e);
        }
    }
}