/*
 * Copyright 2002-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.router;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.Message;
import org.springframework.integration.core.MessageChannel;
import org.springframework.integration.message.MessageDeliveryException;
import org.springframework.integration.message.StringMessage;

/**
 * @author Mark Fisher
 */
public class RecipientListRouterTests {

	@Test
	@SuppressWarnings("unchecked")
	public void channelConfig() {
		QueueChannel channel1 = new QueueChannel();
		QueueChannel channel2 = new QueueChannel();
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		channels.add(channel1);
		channels.add(channel2);
		RecipientListRouter router = new RecipientListRouter();
		router.setChannels(channels);
		router.afterPropertiesSet();
		Collection<MessageChannel> channelList = (Collection<MessageChannel>)
				new DirectFieldAccessor(router).getPropertyValue("channels");
		assertEquals(2, channelList.size());
		assertTrue(channelList.contains(channel1));
		assertTrue(channelList.contains(channel2));
	}

	@Test
	public void routeWithChannelList() {
		QueueChannel channel1 = new QueueChannel();
		QueueChannel channel2 = new QueueChannel();
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		channels.add(channel1);
		channels.add(channel2);
		RecipientListRouter router = new RecipientListRouter();
		router.setChannels(channels);
		router.afterPropertiesSet();
		Message<String> message = new StringMessage("test");
		router.handleMessage(message);
		Message<?> result1 = channel1.receive(25);
		assertNotNull(result1);
		assertEquals("test", result1.getPayload());
		Message<?> result2 = channel2.receive(25);
		assertNotNull(result2);
		assertEquals("test", result2.getPayload());
	}

	@Test
	public void routeToSingleChannel() {
		QueueChannel channel = new QueueChannel();
		channel.setBeanName("channel");
		RecipientListRouter router = new RecipientListRouter();
		router.setChannels(Collections.singletonList((MessageChannel) channel));
		Message<String> message = new StringMessage("test");
		router.handleMessage(message);
		Message<?> result1 = channel.receive(25);
		assertNotNull(result1);
		assertEquals("test", result1.getPayload());
		Message<?> result2 = channel.receive(5);
		assertNull(result2);
	}

	@Test(expected = MessageDeliveryException.class)
	public void sendFailureOnFirstRecipientTriggersExceptionByDefault() {
		QueueChannel channelA = new QueueChannel(1);
		QueueChannel channelB = new QueueChannel(1);
		QueueChannel channelC = new QueueChannel(1);
		channelA.setBeanName("channelA");
		channelB.setBeanName("channelB");
		channelC.setBeanName("channelC");
		RecipientListRouter router = new RecipientListRouter();
		router.setTimeout(0);
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		channels.add(channelA);
		channels.add(channelB);
		channels.add(channelC);
		router.setChannels(channels);
		channelA.send(new StringMessage("blocker"));
		Message<String> message = new StringMessage("test");
		try {
			router.handleMessage(message);
		}
		catch (RuntimeException e) {
			Message<?> result1a = channelA.receive(0);
			assertNotNull(result1a);
			assertEquals("blocker", result1a.getPayload());
			assertNull(channelB.receive(0));
			assertNull(channelC.receive(0));
			throw e;
		}
	}

	@Test(expected = MessageDeliveryException.class)
	public void sendFailureOnMiddleRecipientTriggersExceptionByDefault() {
		QueueChannel channelA = new QueueChannel(1);
		QueueChannel channelB = new QueueChannel(1);
		QueueChannel channelC = new QueueChannel(1);
		channelA.setBeanName("channelA");
		channelB.setBeanName("channelB");
		channelC.setBeanName("channelC");
		RecipientListRouter router = new RecipientListRouter();
		router.setTimeout(0);
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		channels.add(channelA);
		channels.add(channelB);
		channels.add(channelC);
		router.setChannels(channels);
		channelB.send(new StringMessage("blocker"));
		Message<String> message = new StringMessage("test");
		try {
			router.handleMessage(message);
		}
		catch (RuntimeException e) {
			Message<?> result1a = channelA.receive(0);
			assertNotNull(result1a);
			assertEquals("test", result1a.getPayload());
			Message<?> result1b = channelB.receive(0);
			assertNotNull(result1b);
			assertEquals("blocker", result1b.getPayload());			
			assertNull(channelC.receive(0));
			throw e;
		}
	}

	@Test(expected = MessageDeliveryException.class)
	public void sendFailureOnLastRecipientTriggersExceptionByDefault() {
		QueueChannel channelA = new QueueChannel(1);
		QueueChannel channelB = new QueueChannel(1);
		QueueChannel channelC = new QueueChannel(1);
		channelA.setBeanName("channelA");
		channelB.setBeanName("channelB");
		channelC.setBeanName("channelC");
		RecipientListRouter router = new RecipientListRouter();
		router.setTimeout(0);
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		channels.add(channelA);
		channels.add(channelB);
		channels.add(channelC);
		router.setChannels(channels);
		channelC.send(new StringMessage("blocker"));
		Message<String> message = new StringMessage("test");
		try {
			router.handleMessage(message);
		}
		catch (RuntimeException e) {
			Message<?> result1a = channelA.receive(0);
			assertNotNull(result1a);
			assertEquals("test", result1a.getPayload());
			Message<?> result1b = channelB.receive(0);
			assertNotNull(result1b);
			assertEquals("test", result1b.getPayload());
			Message<?> result1c = channelC.receive(0);
			assertNotNull(result1c);
			assertEquals("blocker", result1c.getPayload());
			throw e;
		}
	}

	@Test
	public void sendFailureOnFirstRecipientIgnored() {
		QueueChannel channelA = new QueueChannel(1);
		QueueChannel channelB = new QueueChannel(1);
		QueueChannel channelC = new QueueChannel(1);
		channelA.setBeanName("channelA");
		channelB.setBeanName("channelB");
		channelC.setBeanName("channelC");
		RecipientListRouter router = new RecipientListRouter();
		router.setIgnoreSendFailures(true);
		router.setTimeout(0);
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		channels.add(channelA);
		channels.add(channelB);
		channels.add(channelC);
		router.setChannels(channels);
		channelA.send(new StringMessage("blocker"));
		Message<String> message = new StringMessage("test");
		router.handleMessage(message);
		Message<?> result1a = channelA.receive(0);
		Message<?> result1b = channelB.receive(0);
		Message<?> result1c = channelC.receive(0);
		assertNotNull(result1a);
		assertNotNull(result1b);
		assertNotNull(result1c);
		assertEquals("blocker", result1a.getPayload());
		assertEquals("test", result1b.getPayload());
		assertEquals("test", result1c.getPayload());
	}

	@Test
	public void sendFailureOnMiddleRecipientIgnored() {
		QueueChannel channelA = new QueueChannel(1);
		QueueChannel channelB = new QueueChannel(1);
		QueueChannel channelC = new QueueChannel(1);
		channelA.setBeanName("channelA");
		channelB.setBeanName("channelB");
		channelC.setBeanName("channelC");
		RecipientListRouter router = new RecipientListRouter();
		router.setIgnoreSendFailures(true);
		router.setTimeout(0);
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		channels.add(channelA);
		channels.add(channelB);
		channels.add(channelC);
		router.setChannels(channels);
		channelB.send(new StringMessage("blocker"));
		Message<String> message = new StringMessage("test");
		router.handleMessage(message);
		Message<?> result1a = channelA.receive(0);
		Message<?> result1b = channelB.receive(0);
		Message<?> result1c = channelC.receive(0);
		assertNotNull(result1a);
		assertNotNull(result1b);
		assertNotNull(result1c);
		assertEquals("test", result1a.getPayload());
		assertEquals("blocker", result1b.getPayload());
		assertEquals("test", result1c.getPayload());
	}

	@Test
	public void sendFailureOnLastRecipientIgnored() {
		QueueChannel channelA = new QueueChannel(1);
		QueueChannel channelB = new QueueChannel(1);
		QueueChannel channelC = new QueueChannel(1);
		channelA.setBeanName("channelA");
		channelB.setBeanName("channelB");
		channelC.setBeanName("channelC");
		RecipientListRouter router = new RecipientListRouter();
		router.setIgnoreSendFailures(true);
		router.setTimeout(0);
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		channels.add(channelA);
		channels.add(channelB);
		channels.add(channelC);
		router.setChannels(channels);
		channelC.send(new StringMessage("blocker"));
		Message<String> message = new StringMessage("test");
		router.handleMessage(message);
		Message<?> result1a = channelA.receive(0);
		Message<?> result1b = channelB.receive(0);
		Message<?> result1c = channelC.receive(0);
		assertNotNull(result1a);
		assertNotNull(result1b);
		assertNotNull(result1c);
		assertEquals("test", result1a.getPayload());
		assertEquals("test", result1b.getPayload());
		assertEquals("blocker", result1c.getPayload());
	}

	@Test
	public void applySequenceNotEnabledByDefault() {
		QueueChannel channelA = new QueueChannel(1);
		QueueChannel channelB = new QueueChannel(1);
		channelA.setBeanName("channelA");
		channelB.setBeanName("channelB");
		RecipientListRouter router = new RecipientListRouter();
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		channels.add(channelA);
		channels.add(channelB);
		router.setChannels(channels);
		Message<String> message = new StringMessage("test");
		router.handleMessage(message);
		Message<?> result1a = channelA.receive(0);
		Message<?> result1b = channelB.receive(0);
		assertNotNull(result1a);
		assertNotNull(result1b);
		assertEquals("test", result1a.getPayload());
		assertEquals(0, result1a.getHeaders().getSequenceNumber().intValue());
		assertEquals(0, result1a.getHeaders().getSequenceSize().intValue());
		assertNull(result1a.getHeaders().getCorrelationId());
		assertEquals("test", result1b.getPayload());
		assertEquals(0, result1b.getHeaders().getSequenceNumber().intValue());
		assertEquals(0, result1b.getHeaders().getSequenceSize().intValue());
		assertNull(result1b.getHeaders().getCorrelationId());
	}

	@Test
	public void applySequenceEnabled() {
		QueueChannel channelA = new QueueChannel(1);
		QueueChannel channelB = new QueueChannel(1);
		channelA.setBeanName("channelA");
		channelB.setBeanName("channelB");
		RecipientListRouter router = new RecipientListRouter();
		router.setApplySequence(true);
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		channels.add(channelA);
		channels.add(channelB);
		router.setChannels(channels);
		Message<String> message = new StringMessage("test");
		router.handleMessage(message);
		Message<?> result1a = channelA.receive(0);
		Message<?> result1b = channelB.receive(0);
		assertNotNull(result1a);
		assertNotNull(result1b);
		assertEquals("test", result1a.getPayload());
		assertEquals(1, result1a.getHeaders().getSequenceNumber().intValue());
		assertEquals(2, result1a.getHeaders().getSequenceSize().intValue());
		assertEquals(message.getHeaders().getId(), result1a.getHeaders().getCorrelationId());
		assertEquals("test", result1b.getPayload());
		assertEquals(2, result1b.getHeaders().getSequenceNumber().intValue());
		assertEquals(2, result1b.getHeaders().getSequenceSize().intValue());
		assertEquals(message.getHeaders().getId(), result1b.getHeaders().getCorrelationId());
	}

	@Test(expected = IllegalArgumentException.class)
	public void nullChannelListRejected() {
		RecipientListRouter router = new RecipientListRouter();
		router.setChannels(null);
		router.afterPropertiesSet();
	}

	@Test(expected = IllegalArgumentException.class)
	public void emptyChannelListRejected() {
		RecipientListRouter router = new RecipientListRouter();
		List<MessageChannel> channels = new ArrayList<MessageChannel>();
		router.setChannels(channels);
		router.afterPropertiesSet();
	}

	@Test(expected = IllegalArgumentException.class)
	public void noChannelListFailsInitialization() {
		RecipientListRouter router = new RecipientListRouter();
		router.afterPropertiesSet();
	}

}