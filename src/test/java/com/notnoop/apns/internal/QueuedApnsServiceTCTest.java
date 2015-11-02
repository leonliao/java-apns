package com.notnoop.apns.internal;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.ApnsService;
import com.notnoop.apns.ApnsNotification.Priority;
import com.notnoop.apns.internal.QueuedApnsServiceTest.ConnectionStub;

public class QueuedApnsServiceTCTest {

	@Test(expected = IllegalStateException.class)
	public void sendWithoutStarting() {
		QueuedApnsService service = new QueuedApnsService(null);
		service.push(notification);
	}

	ApnsNotification notification = new ApnsNotification(1,
			ApnsNotification.MAXIMUM_EXPIRY, "2342", "{}",
			Priority.SEND_IMMEDIATELY);

	@Test
	public void pushEventually() {
		ConnectionStub connection = spy(new ConnectionStub(0, 1));
		ApnsService service = newService(connection, null);

		service.push(notification);
		connection.semaphore.acquireUninterruptibly();

		verify(connection, times(1)).sendMessage(notification);
	}

	@Test
	public void doNotBlock() {
		final int delay = 10000;
		ConnectionStub connection = spy(new ConnectionStub(delay, 2));
		QueuedApnsService queued = new QueuedApnsService(new ApnsServiceImpl(
				connection, null));
		queued.start();
		long time1 = System.currentTimeMillis();
		queued.push(notification);
		queued.push(notification);
		long time2 = System.currentTimeMillis();
		assertTrue("queued.push() blocks", (time2 - time1) < delay);

		connection.interrupt();
		connection.semaphore.acquireUninterruptibly();
		verify(connection, times(2)).sendMessage(notification);

		queued.stop();
	}

	protected ApnsService newService(ApnsConnection connection,
			ApnsFeedbackConnection feedback) {
		ApnsService service = new ApnsServiceImpl(connection, null);
		ApnsService queued = new QueuedApnsService(service);
		queued.start();
		return queued;
	}
}
