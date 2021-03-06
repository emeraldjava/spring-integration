/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.integration.ip.tcp.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ServerSocketFactory;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.integration.Message;
import org.springframework.integration.ip.tcp.serializer.ByteArrayCrLfSerializer;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.SocketUtils;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;
import org.springframework.util.ReflectionUtils.FieldFilter;


/**
 * @author Gary Russell
 * @since 2.0
 *
 */
public class TcpNioConnectionTests {

	@Test
	public void testWriteTimeout() throws Exception {
		final int port = SocketUtils.findAvailableServerSocket();
		TcpNioClientConnectionFactory factory = new TcpNioClientConnectionFactory("localhost", port);
		factory.setSoTimeout(1000);
		factory.start();
		final CountDownLatch latch = new CountDownLatch(1);
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			@SuppressWarnings("unused")
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket s = server.accept();
					// block so we fill the buffer
					server.accept();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
		try {
			TcpConnection connection = factory.getConnection();
			connection.send(MessageBuilder.withPayload(new byte[1000000]).build());
		} catch (Exception e) {
			assertTrue("Expected SocketTimeoutException, got " + e.getClass().getSimpleName() +
					   ":" + e.getMessage(), e instanceof SocketTimeoutException);
		}
	}

	@Test
	public void testReadTimeout() throws Exception {
		final int port = SocketUtils.findAvailableServerSocket();
		TcpNioClientConnectionFactory factory = new TcpNioClientConnectionFactory("localhost", port);
		factory.setSoTimeout(1000);
		factory.start();
		final CountDownLatch latch = new CountDownLatch(1);
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					byte[] b = new byte[6];
					readFully(socket.getInputStream(), b);
					// block to cause timeout on read.
					server.accept();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
		try {
			TcpConnection connection = factory.getConnection();
			connection.send(MessageBuilder.withPayload("Test").build());
			int n = 0;
			while (connection.isOpen()) {
				Thread.sleep(100);
				if (n++ > 200) {
					break;
				}
			}
			assertTrue(!connection.isOpen());
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		}
	}

	@Test
	public void testMemoryLeak() throws Exception {
		final int port = SocketUtils.findAvailableServerSocket();
		TcpNioClientConnectionFactory factory = new TcpNioClientConnectionFactory("localhost", port);
		factory.setNioHarvestInterval(100);
		factory.start();
		final CountDownLatch latch = new CountDownLatch(1);
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					byte[] b = new byte[6];
					readFully(socket.getInputStream(), b);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
		try {
			TcpConnection connection = factory.getConnection();
			Map<SocketChannel, TcpNioConnection> connections = factory.getConnections();
			assertEquals(1, connections.size());
			connection.close();
			assertTrue(!connection.isOpen());
			// force a wakeup of the selector
			factory.close();
			int n = 0;
			while (connections.size() > 0) {
				Thread.sleep(100);
				if (n++ > 100) {
					break;
				}
			}
			assertEquals(0, connections.size());
		} catch (Exception e) {
			e.printStackTrace();
			fail("Unexpected exception " + e);
		}
		factory.stop();
	}

	@Test
	public void testCleanup() throws Exception {
		TcpNioClientConnectionFactory factory = new TcpNioClientConnectionFactory("localhost", 0);
		factory.setNioHarvestInterval(100);
		Map<SocketChannel, TcpNioConnection> connections = new HashMap<SocketChannel, TcpNioConnection>();
		SocketChannel chan1 = mock(SocketChannel.class);
		SocketChannel chan2 = mock(SocketChannel.class);
		SocketChannel chan3 = mock(SocketChannel.class);
		TcpNioConnection conn1 = mock(TcpNioConnection.class);
		TcpNioConnection conn2 = mock(TcpNioConnection.class);
		TcpNioConnection conn3 = mock(TcpNioConnection.class);
		connections.put(chan1, conn1);
		connections.put(chan2, conn2);
		connections.put(chan3, conn3);
		final List<Field> fields = new ArrayList<Field>();
		ReflectionUtils.doWithFields(SocketChannel.class, new FieldCallback() {

			public void doWith(Field field) throws IllegalArgumentException,
					IllegalAccessException {
				field.setAccessible(true);
				fields.add(field);
			}
		}, new FieldFilter() {

			public boolean matches(Field field) {
				return field.getName().equals("open");
			}});
		Field field = fields.get(0);
		// Can't use Mockito because isOpen() is final
		ReflectionUtils.setField(field, chan1, true);
		ReflectionUtils.setField(field, chan2, true);
		ReflectionUtils.setField(field, chan3, true);
		Selector selector = mock(Selector.class);
		HashSet<SelectionKey> keys = new HashSet<SelectionKey>();
		when(selector.selectedKeys()).thenReturn(keys);
		factory.processNioSelections(1, selector, null, connections);
		assertEquals(3, connections.size()); // all open

		ReflectionUtils.setField(field, chan1, false);
		factory.processNioSelections(1, selector, null, connections);
		assertEquals(3, connections.size()); // interval didn't pass
		Thread.sleep(110);
		factory.processNioSelections(1, selector, null, connections);
		assertEquals(2, connections.size()); // first is closed

		ReflectionUtils.setField(field, chan2, false);
		factory.processNioSelections(1, selector, null, connections);
		assertEquals(2, connections.size()); // interval didn't pass
		Thread.sleep(110);
		factory.processNioSelections(1, selector, null, connections);
		assertEquals(1, connections.size()); // second is closed

		ReflectionUtils.setField(field, chan3, false);
		factory.processNioSelections(1, selector, null, connections);
		assertEquals(1, connections.size()); // interval didn't pass
		Thread.sleep(110);
		factory.processNioSelections(1, selector, null, connections);
		assertEquals(0, connections.size()); // third is closed

		assertEquals(0, TestUtils.getPropertyValue(factory, "connections", List.class).size());
	}

	@Test
	public void testInsufficientThreads() throws Exception {
		final ExecutorService exec = Executors.newFixedThreadPool(2);
		Future<Object> future = exec.submit(new Callable<Object>() {
			public Object call() throws Exception {
				SocketChannel channel = mock(SocketChannel.class);
				Socket socket = mock(Socket.class);
				Mockito.when(channel.socket()).thenReturn(socket);
				doAnswer(new Answer<Integer>() {
					public Integer answer(InvocationOnMock invocation) throws Throwable {
						ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
						buffer.position(1);
						return 1;
					}
				}).when(channel).read(Mockito.any(ByteBuffer.class));
				when(socket.getReceiveBufferSize()).thenReturn(1024);
				final TcpNioConnection connection = new TcpNioConnection(channel, false, false, null, null);
				connection.setTaskExecutor(exec);
				connection.setPipeTimeout(200);
				Method method = TcpNioConnection.class.getDeclaredMethod("doRead");
				method.setAccessible(true);
				// Nobody reading, should timeout on 6th write.
				try {
					for (int i = 0; i < 6; i++) {
						method.invoke(connection);
					}
				}
				catch (Exception e) {
					e.printStackTrace();
					throw (Exception) e.getCause();
				}
				return null;
			}
		});
		try {
			Object o = future.get(10, TimeUnit.SECONDS);
			fail("Expected exception, got " + o);
		}
		catch (ExecutionException e) {
			assertEquals("Timed out writing to ChannelInputStream, probably due to insufficient threads in " +
					"a fixed thread pool; consider increasing this task executor pool size", e.getCause()
					.getMessage());
		}
	}

	@Test
	public void testSufficientThreads() throws Exception {
		final ExecutorService exec = Executors.newFixedThreadPool(3);
		final CountDownLatch messageLatch = new CountDownLatch(1);
		Future<Object> future = exec.submit(new Callable<Object>() {
			public Object call() throws Exception {
				SocketChannel channel = mock(SocketChannel.class);
				Socket socket = mock(Socket.class);
				Mockito.when(channel.socket()).thenReturn(socket);
				doAnswer(new Answer<Integer>() {
					public Integer answer(InvocationOnMock invocation) throws Throwable {
						ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
						buffer.position(1025);
						buffer.put((byte) '\r');
						buffer.put((byte) '\n');
						return 1027;
					}
				}).when(channel).read(Mockito.any(ByteBuffer.class));
				final TcpNioConnection connection = new TcpNioConnection(channel, false, false, null, null);
				connection.setTaskExecutor(exec);
				connection.registerListener(new TcpListener(){
					public boolean onMessage(Message<?> message) {
						messageLatch.countDown();
						return false;
					}
				});
				connection.setMapper(new TcpMessageMapper());
				connection.setDeserializer(new ByteArrayCrLfSerializer());
				Method method = TcpNioConnection.class.getDeclaredMethod("doRead");
				method.setAccessible(true);
				try {
					for (int i = 0; i < 20; i++) {
						method.invoke(connection);
					}
				}
				catch (Exception e) {
					e.printStackTrace();
					throw (Exception) e.getCause();
				}
				return null;
			}
		});
		future.get(60, TimeUnit.SECONDS);
		assertTrue(messageLatch.await(10, TimeUnit.SECONDS));
	}

	private void readFully(InputStream is, byte[] buff) throws IOException {
		for (int i = 0; i < buff.length; i++) {
			buff[i] = (byte) is.read();
		}
	}

}
