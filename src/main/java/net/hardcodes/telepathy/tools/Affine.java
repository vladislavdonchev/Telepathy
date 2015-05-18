package net.hardcodes.telepathy.tools;

import android.os.Build;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.Telepathy;

import java.math.BigInteger;

/**
 * Created by StereoPor on 19.5.2015 г..
 */
public class Affine {

    public static String getSecretKeyFactoryAlgorithm() {
        return decrypt("NNaaattaN5attNaa", Build.VERSION_CODES.KITKAT);
    }

    public static String SecretKeySpecAlgorithm() {
        return decrypt("Oaaasa", Build.VERSION_CODES.JELLY_BEAN_MR2);
    }

    public static String getCipherAlgorithm() {
        return decrypt("Saaaoa55aSa55Saaa5Saooaoa", Build.VERSION_CODES.ICE_CREAM_SANDWICH);
    }

    private static String decrypt(String input, int seed) {
        input = seed == Build.VERSION_CODES.ICE_CREAM_SANDWICH ? input.replace(
                String.valueOf(Build.VERSION_CODES.ECLAIR * Build.VERSION_CODES.HONEYCOMB), "/") : input;

        int firstKey = Integer.parseInt(Telepathy.getContext().getString(R.string.api_version).split(".")[1])
                * seed;
        int secondKey = Integer.parseInt(Telepathy.getContext().getString(R.string.api_version).split(".")[2])
                * seed;
        int module = Integer.parseInt(Telepathy.getContext().getString(R.string.api_version).split(".")[3])
                * seed;

        StringBuilder builder = new StringBuilder();
        // compute firstKey^-1 aka "modular inverse"
        BigInteger inverse = BigInteger.valueOf(firstKey).modInverse(BigInteger.valueOf(module));
        // perform actual decryption
        for (int in = 0; in < input.length(); in++) {
            char character = input.charAt(in);
            if (Character.isLetter(character)) {
                int decoded = inverse.intValue() * (character - 'a' - secondKey + module);
                character = (char) (decoded % module + 'a');
            }
            builder.append(character);
        }
        return builder.toString();
    }
}
