/*
 * Copyright: Almende B.V. (2014), Rotterdam, The Netherlands
 * License: The Apache Software License, Version 2.0
 */
package com.almende.eve.algorithms.simulation;

import com.almende.eve.scheduling.SimpleSchedulerConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class PersistentSchedulerConfig.
 */
public class SimulationSchedulerConfig extends SimpleSchedulerConfig {

	/**
	 * Instantiates a new simple scheduler config.
	 */
	public SimulationSchedulerConfig() {
		super();
		setClassName(SimulationSchedulerBuilder.class.getName());
	}

	/**
	 * Instantiates a new simple scheduler config.
	 * 
	 * @param node
	 *            the node
	 */
	public static SimulationSchedulerConfig decorate(final ObjectNode node) {
		final SimulationSchedulerConfig res = new SimulationSchedulerConfig();
		res.copy(node);
		return res;
	}
}
