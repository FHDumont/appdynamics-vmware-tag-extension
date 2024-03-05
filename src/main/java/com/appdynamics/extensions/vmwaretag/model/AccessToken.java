package com.appdynamics.extensions.vmwaretag.model;

public class AccessToken {

	public String access_token = null;
	public long expires_in = 0;

	public String getAccessToken() {
		return access_token;
	}

	@Override
	public String toString() {
		return String.format("Access Token [%s] and Expires in [%s]", access_token, expires_in);
	}
}
