/*
 * Copyright: Almende B.V. (2014), Rotterdam, The Netherlands
 * License: The Apache Software License, Version 2.0
 */
package com.almende.eve.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;

import org.junit.Test;

import com.almende.eve.agent.Agent;
import com.almende.eve.agent.AgentBuilder;
import com.almende.eve.agent.ExampleAgent;
import com.almende.eve.config.Config;
import com.almende.eve.config.YamlReader;
import com.almende.util.URIUtil;
import com.almende.util.callback.AsyncCallback;
import com.almende.util.jackson.JOM;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class TestConfigDOM.
 */
public class TestConfigDOM extends TestCase {
	private static final Logger	LOG	= Logger.getLogger(TestConfigDOM.class
											.getName());

	/**
	 * Test agents from DOM.
	 * 
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	@Test
	public void testDOM() throws IOException {
		// First obtain the configuration:
		final Config config = YamlReader.load(new FileInputStream(new File(
				"target/classes/test.yaml")));

		config.loadTemplates("templates");

		final ArrayNode agents = (ArrayNode) config.get("agents");
		Agent agent = null;
		for (final JsonNode agentConf : agents) {
			agent = new AgentBuilder().withConfig((ObjectNode) agentConf)
					.build();
			LOG.info("Created agent:" + agent.getId());
		}
		final ObjectNode params = JOM.createObjectNode();
		params.put("message", "Hi There!");
		ExampleAgent newAgent = (ExampleAgent) agent;
		newAgent.pubSend(URIUtil.create("local:example"), "helloWorld", params,
				new AsyncCallback<String>() {

					@Override
					public void onSuccess(final String result) {
						LOG.warning("Received:'" + result + "'");
					}

					@Override
					public void onFailure(final Exception exception) {
						LOG.log(Level.SEVERE, "", exception);
						fail();
					}

				});

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {}

		LOG.warning("Agent config:" + newAgent.getConfig());

		try {
			Thread.sleep(40000);
		} catch (InterruptedException e) {}

	}
}
