package net.hardcodes.telepathy.tools;

// -----------------------------------------------------------------------------
// StringEncrypter.java
// -----------------------------------------------------------------------------

/*
 * =============================================================================
 * Copyright (c) 1998-2011 Jeffrey M. Hunter. All rights reserved.
 *
 * All source code and material located at the Internet address of
 * http://www.idevelopment.info is the copyright of Jeffrey M. Hunter and
 * is protected under copyright laws of the United States. This source code may
 * not be hosted on any other site without my express, prior, written
 * permission. Application to host any of the material elsewhere can be made by
 * contacting me at jhunter@idevelopment.info.
 *
 * I have made every effort and taken great care in making sure that the source
 * code and other content included on my web site is technically accurate, but I
 * disclaim any and all responsibility for any loss, damage or destruction of
 * data or any other property which may arise from relying on it. I will in no
 * case be liable for any monetary damages arising from such loss, damage or
 * destruction.
 *
 * As with any code, ensure to test this code in a development environment
 * before attempting to run it in production.
 * =============================================================================
 */

// CIPHER / GENERATORS

import android.util.Base64;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.Telepathy;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

// KEY SPECIFICATIONS
// EXCEPTIONS

/**
 * -----------------------------------------------------------------------------
 * The following example implements a class for encrypting and decrypting
 * strings using several Cipher algorithms. The class is created with a key and
 * can be used repeatedly to encrypt and decrypt strings using that key.
 * Some of the more popular algorithms are:
 * Blowfish
 * DES
 * DESede
 * PBEWithMD5AndDES
 * PBEWithMD5AndTripleDES
 * TripleDES
 *
 * @author Jeffrey M. Hunter  (jhunter@idevelopment.info)
 * @author http://www.idevelopment.info
 *         -----------------------------------------------------------------------------
 * @version 1.0
 */

public class StringEncrypter {

    Cipher ecipher;
    Cipher dcipher;

    StringEncrypter(String passPhrase) {

        // 8-bytes Salt
        byte[] salt = {
                (byte) 0xA9, (byte) 0x9B, (byte) 0xC8, (byte) 0x32,
                (byte) 0x56, (byte) 0x34, (byte) 0xE3, (byte) 0x03
        };

        // Iteration count
        int iterationCount = 19;

        try {
            char[] algorithmAnagram = Telepathy.getContext().getString(R.string.server_fingerprint).replace(" ", "").toCharArray();
            String algorithm = new StringBuilder()
                    .append(Character.toUpperCase(algorithmAnagram[8]))
                    .append(algorithmAnagram[0])
                    .append(Character.toUpperCase(algorithmAnagram[4]))
                    .append(Character.toUpperCase(algorithmAnagram[12]))
                    .append(algorithmAnagram[13])
                    .append(algorithmAnagram[9])
                    .append(algorithmAnagram[10])
                    .append(Character.toUpperCase(algorithmAnagram[14]))
                    .append(algorithmAnagram[6])
                    .append(0x5)
                    .append(algorithmAnagram[1])
                    .append(algorithmAnagram[2])
                    .append(algorithmAnagram[3])
                    .append(Character.toUpperCase(algorithmAnagram[5]))
                    .append(Character.toUpperCase(algorithmAnagram[4]))
                    .append(algorithmAnagram[11])
                    .toString();
            KeySpec keySpec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount);
            SecretKey key = SecretKeyFactory.getInstance(algorithm).generateSecret(keySpec);

            ecipher = Cipher.getInstance(key.getAlgorithm());
            dcipher = Cipher.getInstance(key.getAlgorithm());

            AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, iterationCount);

            ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
            dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);

        } catch (Exception e) {
        }
    }


    /**
     * Takes a encrypted String as an argument, decrypts and returns the
     * decrypted String.
     *
     * @param str Encrypted String to be decrypted
     * @return <code>String</code> Decrypted version of the provided String
     */
    private String decrypt(String str) {

        try {

            // Decode base64 to get bytes
            byte[] dec = Base64.decode(str, Base64.DEFAULT);

            // Decrypt
            byte[] utf8 = dcipher.doFinal(dec);

            // Decode using utf-8
            return new String(utf8, "UTF8");

        } catch (Exception e) {
        }
        return null;
    }

    public String getSecretKeyFactoryAlgorithm() {
        return decrypt("dPRCCNbjG1xqhTrCshbcZck9nWHnMnKv");
    }

    public String SecretKeySpecAlgorithm() {
        return decrypt("ZKm4gDggjso=");
    }

    public String getCipherAlgorithm() {
        return decrypt("moVcKk79e3aReDnhvgPlU7drpXB6TgRl");
    }
}