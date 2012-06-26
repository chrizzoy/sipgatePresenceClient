package com.sipgate.web.presence.client.ssl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.SchemeLayeredSocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;


/**
 * A factory for creating SimpleSSLSocket objects.
 */
public class SimpleSSLSocketFactory implements SchemeLayeredSocketFactory
{

	/** The context. */
	private SSLContext context = null;


	/**
	 * Creates a new SimpleSSLSocket object.
	 *
	 * @return the SSL context
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private static SSLContext createSimpleSSLContext() throws IOException
	{
		try
		{
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { new SimpleX509TrustManager() }, null);
			return context;
		}
		catch (Exception e)
		{
			throw new IOException(e.getMessage());
		}
	}

	/**
	 * Gets the SSL context.
	 *
	 * @return the sSL context
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private SSLContext getSSLContext() throws IOException
	{
		if (this.context == null)
		{
			this.context = createSimpleSSLContext();
		}

		return this.context;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.apache.http.conn.scheme.SchemeSocketFactory#createSocket(org.apache
	 * .http.params.HttpParams)
	 */
	public Socket createSocket(HttpParams params) throws IOException
	{
		return getSSLContext().getSocketFactory().createSocket();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.apache.http.conn.scheme.SchemeSocketFactory#connectSocket(java.net
	 * .Socket, java.net.InetSocketAddress, java.net.InetSocketAddress,
	 * org.apache.http.params.HttpParams)
	 */
	public Socket connectSocket(Socket sock, InetSocketAddress remoteAddress, InetSocketAddress localAddress,
			HttpParams params)
			throws IOException, UnknownHostException, ConnectTimeoutException
	{

		int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
		int soTimeout = HttpConnectionParams.getSoTimeout(params);

		SSLSocket sslsock = (SSLSocket) ((sock != null) ? sock : createSocket(params));

		if (localAddress != null)
		{
			sslsock.bind(localAddress);
		}

		sslsock.connect(remoteAddress, connTimeout);
		sslsock.setSoTimeout(soTimeout);
		return sslsock;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.apache.http.conn.scheme.SchemeSocketFactory#isSecure(java.net.Socket)
	 */
	public boolean isSecure(Socket sock) throws IllegalArgumentException
	{
		return true;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.apache.http.conn.scheme.SchemeLayeredSocketFactory#createLayeredSocket
	 * (java.net.Socket, java.lang.String, int,
	 * org.apache.http.params.HttpParams)
	 */
	public Socket createLayeredSocket(Socket socket, String target, int port, HttpParams params)
			throws IOException, UnknownHostException
	{
		return getSSLContext().getSocketFactory().createSocket(socket, target, port, true);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj)
	{
		return ((obj != null) && obj.getClass().equals(getClass()));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode()
	{
		return getClass().hashCode();
	}
}