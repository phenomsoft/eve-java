/*
 * Copyright: Almende B.V. (2014), Rotterdam, The Netherlands
 * License: The Apache Software License, Version 2.0
 */
package com.almende.eve.transport.zmq;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import com.almende.eve.transport.TransportConfig;
import com.almende.util.URIUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class ZmqTransportConfig.
 */
public class ZmqTransportConfig extends TransportConfig {
	private static final Logger	LOG		= Logger.getLogger(ZmqTransportConfig.class
												.getName());
	private static final String	BUILDER	= ZmqTransportBuilder.class.getName();

	/**
	 * Instantiates a new xmpp transport config.
	 */
	protected ZmqTransportConfig() {
		super();
	}

	/**
	 * Instantiates a new ZMQ transport config.
	 *
	 * @return the zmq transport config
	 */
	public static ZmqTransportConfig create() {
		final ZmqTransportConfig res = new ZmqTransportConfig();
		res.setBuilder(BUILDER);
		return res;
	}

	/**
	 * Instantiates a new ZMQ transport config.
	 *
	 * @param node
	 *            the node
	 * @return the zmq transport config
	 */
	public static ZmqTransportConfig decorate(final ObjectNode node) {
		final ZmqTransportConfig res = new ZmqTransportConfig();
		res.extend(node);
		return res;
	}

	/**
	 * Gets the address.
	 * 
	 * @return the address
	 */
	public URI getAddress() {
		if (this.has("address")) {
			try {
				return URIUtil.parse(this.get("address").asText()
						+ (getId() != null ? getId() : ""));
			} catch (final URISyntaxException e) {
				LOG.warning("Couldn't parse URI from: "
						+ this.get("address").asText());
			}
		}
		return null;
	}

	/**
	 * Sets the address.
	 * 
	 * @param address
	 *            the new address
	 */
	public void setAddress(final String address) {
		this.put("address", address);
	}

	/**
	 * Sets the id.
	 * 
	 * @param id
	 *            the new id
	 */
	public void setId(final String id) {
		this.put("id", id);
	}

	/**
	 * Gets the id.
	 * 
	 * @return the id
	 */
	public String getId() {
		if (this.has("id")) {
			return this.get("id").asText();
		}
		return null;
	}
}
