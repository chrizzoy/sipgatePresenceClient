package com.sipgate.web.presence;

import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sipgate.web.presence.client.SipgatePresenceCallback;
import com.sipgate.web.presence.client.SipgatePresenceClient;

/**
 * The Class TestProgram.
 */
public class TestProgram implements SipgatePresenceCallback
{
	/** The logger. */
	private Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args)
	{
		new TestProgram();
	}

	/**
	 * Instantiates a new test program.
	 */
	public TestProgram()
	{
		String host = SipgatePresenceClient.SIPGATE_LIVE_URL;
		String username = "username@email.com";
		String password = "secret";

		SipgatePresenceClient client = new SipgatePresenceClient(host, username, password);
		client.registerPresenceCallback(this);
		client.start();
	}

	/* (non-Javadoc)
	 * @see com.sipgate.web.presence.client.SipgatePresenceCallback#presenceUpdateReceived(org.json.JSONObject)
	 */
	@SuppressWarnings("unchecked")
	public void presenceUpdateReceived(JSONObject json)
	{
		logger.info("Got Update");
		Iterator<String> iterator = json.keys();
		while (iterator.hasNext())
		{
			try
			{
				String key = iterator.next();
				logger.info("for: {} - {}", key, json.getJSONObject(key).toString());
			}
			catch (JSONException e)
			{
				logger.error("Error while getting jsonObject", e);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.sipgate.web.presence.client.SipgatePresenceCallback#presenceErrorOccured(java.lang.Exception)
	 */
	public void presenceErrorOccured(Exception exception)
	{
		logger.error("ERROR", exception);
	}
}
