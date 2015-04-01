package net.hardcodes.telepathy.model;

import com.google.gson.annotations.SerializedName;

/**
 * Created by StereoPor on 29.3.2015 ?..
 */
public class User {

    public final static int LICENSE_TYPE_PERSONAL = 0x1;
    public final static int LICENSE_TYPE_PROFESSIONAL = 0x2;
    public final static int LICENSE_TYPE_BUSINESS = 0x3;

    @SerializedName("userName")
    private String userName;

    @SerializedName("passwordHash")
    private String passwordHash;

    @SerializedName("registrationTimestamp")
    private long registrationTimestamp;

    @SerializedName("lastLoginTimestamp")
    private long lastLoginTimestamp;

    @SerializedName("mailAddress")
    private String mailAddress;

    @SerializedName("licenseType")
    private int licenseType = LICENSE_TYPE_PERSONAL;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public long getRegistrationTimestamp() {
        return registrationTimestamp;
    }

    public void setRegistrationTimestamp(long registrationTimestamp) {
        this.registrationTimestamp = registrationTimestamp;
    }

    public long getLastLoginTimestamp() {
        return lastLoginTimestamp;
    }

    public void setLastLoginTimestamp(long lastLoginTimestamp) {
        this.lastLoginTimestamp = lastLoginTimestamp;
    }

    public String getMailAddress() {
        return mailAddress;
    }

    public void setMailAddress(String mailAddress) {
        this.mailAddress = mailAddress;
    }

    public int getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(int licenseType) {
        this.licenseType = licenseType;
    }
}