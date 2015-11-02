package com.notnoop.apns.internal;

import org.junit.Test;

import static org.mockito.Mockito.*;

import com.notnoop.apns.ApnsService;
import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.ApnsNotification.Priority;

public class ApnsServiceImplTest {

	ApnsNotification notification = new ApnsNotification(1,
			ApnsNotification.MAXIMUM_EXPIRY, "2342", "{}",
			Priority.SEND_IMMEDIATELY);

	@Test
	public void pushEventually() {
		ApnsConnection connection = mock(ApnsConnection.class);
		ApnsService service = newService(connection, null);

		service.push(notification);

		verify(connection, times(1)).sendMessage(notification);
	}

	@Test
	public void pushEventuallySample() {
		ApnsConnection connection = mock(ApnsConnection.class);
		ApnsService service = newService(connection, null);

		service.push("2342", "{}");

		verify(connection, times(1)).sendMessage(notification);
	}

	protected ApnsService newService(ApnsConnection connection,
			ApnsFeedbackConnection feedback) {
		return new ApnsServiceImpl(connection, null);
	}
}
