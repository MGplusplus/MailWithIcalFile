package com.amazonaws.lambda.demo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.json.simple.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
//import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.UidGenerator;

public class MailWithIcal implements RequestHandler<JSONObject, String> {

	@Override
	public String handleRequest(JSONObject input, Context context) {
		context.getLogger().log("Input: " + input);
		
		// Extracting values from JSON object.
		String loginUserName = (String) input.get("userName");
		String loginPassword = (String) input.get("password");
		String senderName = (String) input.get("senderName");
		String recipientName = (String) input.get("recipientName");
		String recipientEmail = (String) input.get("recipientEmail");
		String eventName = (String) input.get("eventName");
		String eventLocation = (String) input.get("location");
		String month = (String) input.get("month");
		String date = (String) input.get("date");
		String year = (String) input.get("year");
		String eventStartHr = (String) input.get("startHr");
		String eventStartMin = (String) input.get("startMin");
		String eventEndHr = (String) input.get("endHr");
		String eventEndMin = (String) input.get("endMin");
		
		// createCalendar function is to create a calendar object and save an ical file in /tmp folder of AWS.
		try {
			createCalendar(month, date, year, eventName, recipientName, eventLocation, eventStartHr, eventStartMin,
						eventEndHr, eventEndMin);
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (ValidationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// sendEmailWithCalFile funtion is sending an email with an ical Attachment file.
		try {
			sendEmailWithCalFile(loginUserName, loginPassword, senderName, recipientName, recipientEmail, eventStartHr,
						eventStartMin, eventLocation);
		} catch (AddressException e) {
			e.printStackTrace();
		} catch (MessagingException e) {
			e.printStackTrace();
		}
		
		
		// updateUserGoogleCal function is updating user's google calendar.
		
		try {
			updateUserGoogleCal(month, date, year, eventName, recipientName, eventLocation, eventStartHr, eventStartMin,
							eventEndHr, eventEndMin);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		
		

		return "Your process successfully done.";
	}

	public void createCalendar(String month, String date, String year, String eventName, String recepientName,
			String evenLocation, String eventStartHr, String eventStartMin, String eventEndHr, String eventEndMin) throws ParseException, ValidationException, IOException {
		String calFile = "/tmp/Calendar.ics";

		// Creating a new calendar
		net.fortuna.ical4j.model.Calendar calendar = new net.fortuna.ical4j.model.Calendar();
		calendar.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
		calendar.getProperties().add(Version.VERSION_2_0);
		calendar.getProperties().add(CalScale.GREGORIAN);

		TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
		TimeZone timezone = registry.getTimeZone("Etc/GMT");
		
		// Creating a VEVENT for creating an ical file.
		SimpleDateFormat df = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss");
		java.util.Calendar startCal = java.util.Calendar.getInstance(timezone);

		
		startCal.setTime(df.parse(month + "-" + date + "-" + year + " " + eventStartHr + ":" + eventStartMin + ":" + "00"));
		java.util.Calendar endCal = java.util.Calendar.getInstance(timezone);

		SimpleDateFormat sdfDate = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss");
		
		endCal.setTime(sdfDate.parse(month + "-" + date + "-" + year + " " + eventEndHr + ":" + eventEndMin + ":" + "00"));
		

		net.fortuna.ical4j.model.Date dtStart = new DateTime(startCal.getTime());
		net.fortuna.ical4j.model.Date dtEnd = new DateTime(endCal.getTime());

		VEvent event = new VEvent(dtStart, dtEnd, eventName);

		// initialize an event..
		event.getProperties().getProperty(Property.DTSTART).getParameters().add(Value.DATE);
		event.getProperties().add(new Location(evenLocation));
		event.getProperties().add(new Description("Hello " + recepientName + ", You have an appointment"));

		UidGenerator eventUid = null;
		
		eventUid = new UidGenerator("1");
		
		event.getProperties().add(eventUid.generateUid());

		calendar.getComponents().add(event);

		// Saving an iCalendar file
		FileOutputStream fout = new FileOutputStream(calFile);
		fout.close(); 
			

		CalendarOutputter outputter = new CalendarOutputter();
		outputter.setValidating(false);
		outputter.output(calendar, fout);
	
	}

	public void sendEmailWithCalFile(String userName, String password, String senderName, String recepientName,
			String recipientEmail, String startHr, String startMin, String location) throws AddressException, MessagingException {
		Properties props = new Properties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.port", "587");

		Session session = Session.getInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(userName, password);
			}
		});
		

		Message message = new MimeMessage(session);
		message.setFrom(new InternetAddress(recipientEmail));
		message.setRecipients(RecipientType.TO, InternetAddress.parse(recipientEmail));
		message.addRecipient(RecipientType.BCC, new InternetAddress("dummydump101@gmail.com"));
		message.addRecipient(RecipientType.CC, new InternetAddress("dummydump101@gmail.com"));
		message.setSubject("Please find the attachment(Calendar File).");

		// Reading ical file from the tmp loaction and attaching it to the email.
		BodyPart messageBodyPart = new MimeBodyPart();
		MimeMultipart multipart = new MimeMultipart();
		
		String file = "/tmp/Calendar.ics";
		String fileName = "CalendarFile.ics";
		
		//reading ical file from source.
		DataSource source = new FileDataSource(file);
		
		messageBodyPart.setDataHandler(new DataHandler(source));
		messageBodyPart.setFileName(fileName);
		multipart.addBodyPart(messageBodyPart);
		message.setContent(multipart);
		
		// creating body of email.
		BodyPart content = new MimeBodyPart();
		content.setContent("Hello " + recepientName + ", <html><br></html>We confirm your appointment at our "
				+ location + " office" + ".<html><br></html>Thank you for choosing us.", "text/html");
		multipart.addBodyPart(content);
		message.setContent(multipart);

		// sending email through smtp.
		Transport.send(message);
		

	}

	public void updateUserGoogleCal(String month, String date, String year, String eventName, String recepientName,
			String location, String startHr, String startMin, String endHr, String endMin)
			throws GeneralSecurityException, IOException, URISyntaxException {
		
		final String APPLICATION_NAME = "Google Calendar";
		JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
		final String TOKENS_DIRECTORY_PATH = "tokens";
		final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
		final String CREDENTIALS_FILE_PATH = "/credentials_For_GoogleCaljson.json";

		// Build a new authorized API client service.
		final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

		Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				getCredentials(HTTP_TRANSPORT, JSON_FACTORY, SCOPES, TOKENS_DIRECTORY_PATH, CREDENTIALS_FILE_PATH))
						.setApplicationName(APPLICATION_NAME).build();

		// Creating an Google calendar simple Event for google api.
		Event event = new Event().setSummary("Hello Sir, This is very urgent.").setLocation("GNR-Gandhinagar, Gujrat")
				.setDescription("Important Meeting for the developers");
		
		// Setting the start time of the event.
		com.google.api.client.util.DateTime startDateTime = new com.google.api.client.util.DateTime(
				year + "-" + month + "-" + date + "T" + startHr + ":" + startMin + ":00+05:30");
		
		EventDateTime start = new EventDateTime().setDateTime(startDateTime).setTimeZone("GMT");
		event.setStart(start);

		// Setting the end time of the event.
		com.google.api.client.util.DateTime endDateTime = new com.google.api.client.util.DateTime(
				year + "-" + month + "-" + date + "T" + endHr + ":" + endMin + ":00+05:30");
		EventDateTime end = new EventDateTime().setDateTime(endDateTime).setTimeZone("GMT");
		event.setEnd(end);
		
		// Setting the recurrence of event to count 2 on daily basis
		String[] recurrence = new String[] { "RRULE:FREQ=DAILY;COUNT=2" };
		event.setRecurrence(Arrays.asList(recurrence));
		
		//Adding the Event Attendees.
		EventAttendee[] attendees = new EventAttendee[] {
				new EventAttendee().setEmail("khushal@example.com"),
				new EventAttendee().setEmail("mohit@example.com"),
				};
		event.setAttendees(Arrays.asList(attendees));
		
		//setting the reminder in event.
		EventReminder[] reminderOverrides = new EventReminder[] {
				new EventReminder().setMethod("email").setMinutes(24 * 60),
				new EventReminder().setMethod("popup").setMinutes(10),
				};
		Event.Reminders reminders = new Event.Reminders().setUseDefault(false)
				.setOverrides(Arrays.asList(reminderOverrides));
		event.setReminders(reminders);

		// Selecting the primary calendar of user.
		String calendarId = "primary";
		//Inserting event in user's calendar
		event = service.events().insert(calendarId, event).execute();
		System.out.printf("Event created: %s\n", event.getHtmlLink());

	}

	static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT, JsonFactory JSON_FACTORY,
			final List<String> SCOPES, final String TOKENS_DIRECTORY_PATH, final String CREDENTIALS_FILE_PATH)
			throws IOException, URISyntaxException {
		
		/** Reading the token available in resources directory and copying the token into /tmp directory.
		 	resource folder has only read-only permissions, but google api require both read and write permissions,
		 	so make a copy of token into /tmp location.
		**/
		
		// reading google credentials from the resources directory
		Path pathTillCodeDirectory = Paths.get(MailWithIcal.class.getResource("/").toURI());
		String googleCredentialLocation = pathTillCodeDirectory + "/resources/credentials_For_GoogleCal.json";

		FileInputStream inputs = new FileInputStream(googleCredentialLocation);
		// converting Fileinputstream to Inputstream, it needs to done explicitly otherwise sometime lambda function give error.
		InputStream input = inputs;

		// craeting client secret object.
		GoogleClientSecrets clientSecret = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(input, "UTF-8"));

		Path pathToCodeLocation = Paths.get(MailWithIcal.class.getResource("/").toURI());
		String tokenLocResource = pathToCodeLocation + "/resources/tokens";

		String newLocTokenTmp = "/tmp/tokens";

		File source = new File(tokenLocResource);
		File dest = new File(newLocTokenTmp);

		// copy the token from resource to tmp directory
		FileUtils.copyDirectory(source, dest);

		/* 	Authorization process of credentials and receiving token from client side,
			which is permission from client to access there calendar
		*/
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				clientSecret, SCOPES).setDataStoreFactory(new FileDataStoreFactory(new java.io.File(newLocTokenTmp)))
						.setAccessType("offline").build();
		
		return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");

	}
}