package com.drs.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Password hashing and authenticated encryption. Keys are supplied outside source control. */
public final class Crypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;
    public Crypto(String base64Key) {
        key = Base64.getDecoder().decode(base64Key);
        if (key.length != 32) throw new IllegalArgumentException("DRS_DATA_KEY must be a base64 32-byte key");
    }
    public static String randomKey() { byte[] b=new byte[32]; RANDOM.nextBytes(b); return Base64.getEncoder().encodeToString(b); }
    public static String hashPassword(String password) {
        byte[] salt=new byte[16]; RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(password,salt));
    }
    private static byte[] derive(String password, byte[] salt) {
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.toCharArray(),salt,210000,256)).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public static boolean verifyPassword(String password,String stored) {
        try { String[] s=stored.split(":"); return MessageDigest.isEqual(derive(password,Base64.getDecoder().decode(s[0])),Base64.getDecoder().decode(s[1])); }
        catch (RuntimeException e) { return false; }
    }
    public String encrypt(String value) {
        try { byte[] iv=new byte[12]; RANDOM.nextBytes(iv); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            byte[] encrypted=c.doFinal(value.getBytes(StandardCharsets.UTF_8)); byte[] out=new byte[iv.length+encrypted.length]; System.arraycopy(iv,0,out,0,iv.length); System.arraycopy(encrypted,0,out,iv.length,encrypted.length); return Base64.getEncoder().encodeToString(out);
        } catch(GeneralSecurityException e) {throw new IllegalStateException(e);}
    }
    public String decrypt(String value) {
        try { byte[] in=Base64.getDecoder().decode(value); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(in,12))); return new String(c.doFinal(in,12,in.length-12),StandardCharsets.UTF_8); }
        catch(GeneralSecurityException e) {throw new IllegalStateException("Encrypted data failed authentication",e);}
    }
    /** Authenticated audit record; this is tamper evidence, not an independent digital signature. */
    public String mac(String value) {
        try { Mac m=Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(key,"HmacSHA256")); return Base64.getEncoder().encodeToString(m.doFinal(value.getBytes(StandardCharsets.UTF_8))); }
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
}
