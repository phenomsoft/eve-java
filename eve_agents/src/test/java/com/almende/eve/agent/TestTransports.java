/*
 * Copyright: Almende B.V. (2014), Rotterdam, The Netherlands
 * License: The Apache Software License, Version 2.0
 */
package com.almende.eve.agent;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

import junit.framework.TestCase;

import org.junit.Test;

import com.almende.eve.capabilities.handler.Handler;
import com.almende.eve.transport.Receiver;
import com.almende.eve.transport.Transport;
import com.almende.eve.transport.TransportFactory;
import com.almende.util.jackson.JOM;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class TestTransports.
 */
public class TestTransports extends TestCase {
	private static final Logger	LOG	= Logger.getLogger(TestTransports.class
											.getName());
	
	/**
	 * Test Xmpp
	 * 
	 * @throws IOException
	 */
	@Test
	public void testXmpp() throws IOException {
		ObjectNode params = JOM.createObjectNode();
		params.put("class", "com.almende.eve.transport.xmpp.XmppService");
		params.put("address", "xmpp://alex@openid.almende.org/test");
		params.put("password", "alex");
		
		Transport transport = TransportFactory.getTransport(params,
				new myReceiver());
		transport.connect();
		
		transport.send(URI.create("xmpp:gloria@openid.almende.org"),
				"Hello World", null);
		
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
		}
	}
	
	/**
	 * Test Xmpp
	 * 
	 * @throws IOException
	 */
	@Test
	public void testZmq() throws IOException {
		ObjectNode params = JOM.createObjectNode();
		params.put("class", "com.almende.eve.transport.zmq.ZmqService");
		params.put("address", "zmq://tcp://127.0.0.1:5678");
		
		Transport transport = TransportFactory.getTransport(params,
				new myReceiver());
		transport.connect();
		
		transport.send(URI.create("zmq://tcp://127.0.0.1:5678"), "Hello World",
				null);
	}
	
	/**
	 * The Class myReceiver.
	 */
	public class myReceiver implements Receiver, Handler<Receiver> {
		@Override
		public void receive(Object msg, URI senderUrl, String tag) {
			
			LOG.warning("Received msg:'" + msg + "' from: "
					+ senderUrl.toASCIIString());
		}
		
		@Override
		public Receiver get() {
			return this;
		}
		
		@Override
		public void update(Handler<Receiver> newHandler) {
			// Not used, data should be the same.
		}
		
	}
}
