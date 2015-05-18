package net.hardcodes.telepathy.tools;

import android.util.Base64;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.Telepathy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class FileCipher {
    private static SecretKey secretKey;

    public FileCipher() {
        try {
            InputStream secretKeyFile = Telepathy.getContext().getAssets().open("font/squared_display.ttf");
            initCipherWithKey(secretKeyFile);
        } catch (Exception e) {
            Logger.log("CIPHER", e.toString(), e);
        }
    }

    public void initCipherWithKey(InputStream keyInFile) throws Exception {
        secretKey = retrieveKey(Utils.sha256(Telepathy.getContext().getString(R.string.action_generic_dialog_title))
                .toUpperCase().toCharArray(), keyInFile);
    }

    private SecretKey retrieveKey(char[] password, InputStream keyInFile) throws Exception {
        ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
        int byteRead = 0;
        while ((byteRead = keyInFile.read()) != -1) {
            byteArrayOut.write(byteRead);
        }
        keyInFile.close();
        byte[] saltAndKeyBytes = byteArrayOut.toByteArray();
        byteArrayOut.close();

        byte[] salt = new byte[8];
        System.arraycopy(saltAndKeyBytes, 0, salt, 0, 8);

        int keySize = saltAndKeyBytes.length - 8;
        byte[] encryptedKeyBytes = new byte[keySize];
        System.arraycopy(saltAndKeyBytes, 8, encryptedKeyBytes, 0, keySize);

        PBEKeySpec keySpec = new PBEKeySpec(password);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(Affine.getSecretKeyFactoryAlgorithm());
        SecretKey passwordKey = keyFactory.generateSecret(keySpec);
        PBEParameterSpec parameterSpec = new PBEParameterSpec(salt, 1000);

        Cipher cipher = Cipher.getInstance(Affine.getSecretKeyFactoryAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, passwordKey, parameterSpec);

        byte[] decryptedKeyBytes = cipher.doFinal(encryptedKeyBytes);

        SecretKey secretKey = new SecretKeySpec(decryptedKeyBytes, Affine.SecretKeySpecAlgorithm());

        return secretKey;
    }

    public final InputStream readEncryptedFile(InputStream file) throws Exception {
        Cipher cipher = Cipher.getInstance(Affine.getCipherAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(getMagicBytes()));

        CipherInputStream cipherInFile = new CipherInputStream(file, cipher);

        return new ByteArrayInputStream(Base64.decode(Utils.readInputStream(cipherInFile), Base64.DEFAULT));
    }

    private byte[] getMagicBytes() {
        byte[] ivBytes;

        try {
            ivBytes = Utils.sha256(Telepathy.getContext().getString(R.string.action_settings)).toUpperCase().getBytes();
        } catch (Exception e) {
            Logger.log("IV", e.getMessage(), e);
            return null;
        }

        byte[] magicBytes = new byte[8];

        for (int i = 24; i < 32; i++) {
            magicBytes[i - 24] = ivBytes[i];
        }

        return magicBytes;
    }
}