package com.sipgate.web.presence.client.ssl;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * The Class SimpleX509TrustManager.
 */
public class SimpleX509TrustManager implements X509TrustManager
{
	/** The trust manager. */
	private X509TrustManager trustManager = null;


	/**
	 * Instantiates a new simple x509 trust manager.
	 *
	 * @throws NoSuchAlgorithmException the no such algorithm exception
	 * @throws KeyStoreException the key store exception
	 */
	public SimpleX509TrustManager() throws NoSuchAlgorithmException, KeyStoreException
	{
		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init((KeyStore) null);
		TrustManager[] trustmanagers = factory.getTrustManagers();

		if (trustmanagers.length == 0)
		{
			throw new NoSuchAlgorithmException("No trust manager");
		}

		trustManager = (X509TrustManager) trustmanagers[0];
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * javax.net.ssl.X509TrustManager#checkClientTrusted(java.security.cert.
	 * X509Certificate[], java.lang.String)
	 */
	public void checkClientTrusted(X509Certificate[] certificates, String authType) throws CertificateException
	{
		trustManager.checkClientTrusted(certificates, authType);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
	 */
	public X509Certificate[] getAcceptedIssuers()
	{
		return trustManager.getAcceptedIssuers();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * javax.net.ssl.X509TrustManager#checkServerTrusted(java.security.cert.
	 * X509Certificate[], java.lang.String)
	 */
	public void checkServerTrusted(X509Certificate[] certificates, String authType) throws CertificateException
	{
		// Always Trust Server
	}
}