package com.sipgate.web.presence.client;

import org.json.JSONObject;

/**
 * The Interface SipgatePresenceCallback.
 */
public interface SipgatePresenceCallback
{
	/**
	 * Presence update received occurs, when a update is signaled by sipgate
	 * presence backend.
	 *
	 * @param json
	 *            the json-response contains the extension, the alias and the
	 *            phonestate
	 */
	public void presenceUpdateReceived(JSONObject json);

	/**
	 * Presence error occured will be called if any exception got thrown.
	 *
	 * @param exception
	 *            the exception which occured
	 */
	public void presenceErrorOccured(Exception exception);
}
