package com.hux.testwallet;

import android.util.Log;

import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.DeterministicKey;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于BIP39的HD钱包管理器（修正版）
 */
public class Bip39WalletManager {
    private static final String TAG = "Bip39WalletManager";

    private static Bip39WalletManager instance;
    private ObjectMapper objectMapper = new ObjectMapper();

    // HD钱包派生路径 (BIP44: m/44'/60'/0'/0/0)
    private static final String ETH_DERIVATION_PATH = "m/44'/60'/0'/0/0";

    public static Bip39WalletManager getInstance() {
        if (instance == null) {
            synchronized (Bip39WalletManager.class) {
                if (instance == null) {
                    instance = new Bip39WalletManager();
                }
            }
        }
        return instance;
    }

    /**
     * 生成新的BIP39助记词
     */
    public List<String> generateMnemonic() {
        try {
            SecureRandom secureRandom = new SecureRandom();
            byte[] entropy = new byte[16]; // 128位，生成12个单词
            secureRandom.nextBytes(entropy);

            List<String> mnemonics = MnemonicCode.INSTANCE.toMnemonic(entropy);
            Log.d(TAG, "Generated Mnemonic: " + String.join(" ", mnemonics));
            return mnemonics;
        } catch (MnemonicException.MnemonicLengthException e) {
            Log.e(TAG, "Generate mnemonic failed", e);
            return null;
        }
    }

    /**
     * 从助记词创建HD钱包
     */
    public WalletFile createWalletFromMnemonic(List<String> mnemonics, String password) {
        try {
            // 1. 验证助记词
            MnemonicCode.INSTANCE.check(mnemonics);

            // 2. 从助记词生成种子
            byte[] seed = MnemonicCode.toSeed(mnemonics, "");

            // 3. 从种子生成主密钥
            DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed);

            // 4. 按照BIP44路径逐层派生以太坊密钥
            DeterministicKey derivedKey = deriveChildKeyFromPath(masterKey, ETH_DERIVATION_PATH);

            // 5. 创建ECKeyPair
            ECKeyPair ecKeyPair = ECKeyPair.create(derivedKey.getPrivKey());

            // 6. 创建钱包文件
            WalletFile walletFile = Wallet.createLight(password, ecKeyPair);

            Log.d(TAG, "Wallet created from mnemonic, address: " + walletFile.getAddress());
            return walletFile;

        } catch (Exception e) {
            Log.e(TAG, "Create wallet from mnemonic failed", e);
            return null;
        }
    }

    /**
     * 根据BIP44路径逐层派生子密钥
     */
    private DeterministicKey deriveChildKeyFromPath(DeterministicKey parentKey, String path) {
        try {
            DeterministicKey currentKey = parentKey;

            // 去掉开头的 "m/" 或 "m"
            if (path.startsWith("m/")) {
                path = path.substring(2);
            } else if (path.startsWith("m")) {
                path = path.substring(1);
            }

            // 如果路径为空，直接返回主密钥
            if (path.isEmpty()) {
                return currentKey;
            }

            // 分割路径并逐层派生
            String[] pathElements = path.split("/");
            for (String element : pathElements) {
                if (element.isEmpty()) continue;

                boolean isHardened = element.endsWith("'");
                String numberStr = isHardened ? element.substring(0, element.length() - 1) : element;

                try {
                    int childNumber = Integer.parseInt(numberStr);
                    if (isHardened) {
                        // 硬化派生：childNumber + 2^31
                        childNumber = childNumber | 0x80000000;
                    }

                    currentKey = HDKeyDerivation.deriveChildKey(currentKey, childNumber);
                    Log.d(TAG, "Derived child key: " + element + " -> " + currentKey);

                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid path element: " + element);
                    throw new IllegalArgumentException("Invalid derivation path");
                }
            }

            return currentKey;

        } catch (Exception e) {
            Log.e(TAG, "Derive child key from path failed: " + path, e);
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    /**
     * 从同一个助记词派生多个地址
     */
    public WalletFile deriveNewAddress(List<String> mnemonics, String password, int accountIndex) {
        try {
            // 生成种子
            byte[] seed = MnemonicCode.toSeed(mnemonics, "");

            // 生成主密钥
            DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed);

            // 派生新路径: m/44'/60'/0'/0/{index}
            String derivationPath = String.format("m/44'/60'/0'/0/%d", accountIndex);
            DeterministicKey derivedKey = deriveChildKeyFromPath(masterKey, derivationPath);

            // 创建密钥对和钱包
            ECKeyPair ecKeyPair = ECKeyPair.create(derivedKey.getPrivKey());
            WalletFile walletFile = Wallet.createLight(password, ecKeyPair);

            Log.d(TAG, "Derived new address at index " + accountIndex + ": " + walletFile.getAddress());
            return walletFile;

        } catch (Exception e) {
            Log.d(TAG, "Derive new address failed at index: " + accountIndex, e);
            return null;
        }
    }

    /**
     * 从助记词恢复钱包
     */
    public WalletFile recoverWallet(String mnemonicPhrase, String password) {
        try {
            String[] words = mnemonicPhrase.split(" ");
            List<String> mnemonics = new ArrayList<>();
            for (String word : words) {
                mnemonics.add(word.trim());
            }
            return createWalletFromMnemonic(mnemonics, password);
        } catch (Exception e) {
            Log.e(TAG, "Recover wallet failed", e);
            return null;
        }
    }

    /**
     * 验证助记词有效性
     */
    public boolean validateMnemonic(List<String> mnemonics) {
        try {
            MnemonicCode.INSTANCE.check(mnemonics);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 直接从助记词获取私钥（用于调试）
     */
    public String getPrivateKeyFromMnemonic(List<String> mnemonics) {
        try {
            byte[] seed = MnemonicCode.toSeed(mnemonics, "");
            DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed);
            DeterministicKey derivedKey = deriveChildKeyFromPath(masterKey, ETH_DERIVATION_PATH);

            ECKeyPair ecKeyPair = ECKeyPair.create(derivedKey.getPrivKey());
            return "0x" + ecKeyPair.getPrivateKey().toString(16);

        } catch (Exception e) {
            Log.e(TAG, "Get private key from mnemonic failed", e);
            return null;
        }
    }
}