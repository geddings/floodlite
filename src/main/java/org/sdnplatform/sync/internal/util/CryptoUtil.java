package org.sdnplatform.sync.internal.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.sdnplatform.sync.ISyncService;

public class CryptoUtil {
    private static SecureRandom secureRandom = new SecureRandom();

    public static final String CHALLENGE_RESPONSE_SECRET = "AliasChallengeResponse";
    
    public static byte[] secureRandom(int bytes) {
        byte[] r = new byte[bytes];
        secureRandom.nextBytes(r);
        return r;
    }
    
    public static KeyStore readKeyStore(String keyStorePath,
                                        char[] keyStorePassword) 
                                                throws Exception {
        KeyStore ks = KeyStore.getInstance("JCEKS");

        java.io.FileInputStream fis = null;
        try {
            fis = new java.io.FileInputStream(keyStorePath);
            ks.load(fis, keyStorePassword);
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
        return ks;
    }
    
    public static byte[] getSharedSecret(String keyStorePath,
                                         String keyStorePassword) 
                                                    throws Exception {
        if (keyStorePath == null) return null;
       
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray());
        Key key = keyStore.getKey(CHALLENGE_RESPONSE_SECRET, keyStorePassword.toCharArray());
        
        return key.getEncoded();
    }
    
    public static void writeSharedSecret(String keyStorePath,
                                         String keyStorePassword,
                                         byte[] sharedSecret) 
                                                   throws Exception {
        char[] password = keyStorePassword.toCharArray();
        KeyStore ks;
        try {
            ks = readKeyStore(keyStorePath, password);
        } catch (FileNotFoundException e) {
            ks = KeyStore.getInstance("JCEKS");
            ks.load(null, password);
        } 

        KeyStore.ProtectionParameter protParam =
                new KeyStore.PasswordProtection(password);
        SecretKeySpec signingKey = 
                new SecretKeySpec(sharedSecret, "HmacSHA1");
        KeyStore.SecretKeyEntry skEntry =
                new KeyStore.SecretKeyEntry(signingKey);
        ks.setEntry(CHALLENGE_RESPONSE_SECRET, skEntry, protParam);

        // store away the keystore
        java.io.FileOutputStream fos = null;
        File keyStoreFile = new File(keyStorePath);
        File parent = keyStoreFile.getParentFile();
        if (parent != null)
            parent.mkdirs();
        try {
            fos = new java.io.FileOutputStream(keyStoreFile);
            ks.store(fos, password);
            keyStoreFile.setReadable(false, false);
            keyStoreFile.setReadable(true, true);
            keyStoreFile.setWritable(false, false);
            keyStoreFile.setWritable(true, true);
            keyStoreFile.setExecutable(false, false);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }
}
