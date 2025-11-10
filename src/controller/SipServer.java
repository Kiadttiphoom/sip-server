package controller;

import gov.nist.javax.sip.SipStackImpl;
import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal SIP Registrar + Proxy Server using JAIN-SIP (Java JRE 1.8)
 * Compatible with JAIN-SIP 1.2.277
 */
public class SipServer implements SipListener {

    private SipFactory sipFactory;
    private SipStack sipStack;
    private SipProvider sipProvider;
    private MessageFactory messageFactory;
    private HeaderFactory headerFactory;
    private AddressFactory addressFactory;
    
    private final Map<String, String> validUsers = new HashMap<String, String>() {{
        put("1001", "1234");
        put("1002", "1234");
    }};

    private final Map<String, ContactHeader> registeredContacts = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        new SipServer().init();
    }

    private void init() throws Exception {
        // สร้าง SIP Stack
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "JavaSIPServer");
        properties.setProperty("javax.sip.IP_ADDRESS", "0.0.0.0");
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "sipserver_log.txt");
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "sipserver_debug.txt");

        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");

        sipStack = sipFactory.createSipStack(properties);
        headerFactory = sipFactory.createHeaderFactory();
        addressFactory = sipFactory.createAddressFactory();
        messageFactory = sipFactory.createMessageFactory();

        // ฟังที่พอร์ต 5060 (UDP)
        ListeningPoint udp = sipStack.createListeningPoint("0.0.0.0", 5060, ListeningPoint.UDP);
        sipProvider = sipStack.createSipProvider(udp);
        sipProvider.addSipListener(this);

        System.out.println("SIP Server started on UDP port 5060 ...");
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        ServerTransaction st = requestEvent.getServerTransaction();

        try {
            if (method.equals(Request.REGISTER)) {
                processRegister(requestEvent, st);
            } else if (method.equals(Request.INVITE)) {
                processInvite(requestEvent);
            } else if (method.equals(Request.BYE)) {
                processBye(requestEvent);
            } else if (method.equals(Request.OPTIONS)) {
                sendResponse(requestEvent, Response.OK);
            } else {
                sendResponse(requestEvent, Response.METHOD_NOT_ALLOWED);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processRegister(RequestEvent evt, ServerTransaction st) throws Exception {
        Request req = evt.getRequest();
        FromHeader from = (FromHeader) req.getHeader(FromHeader.NAME);
        ContactHeader contact = (ContactHeader) req.getHeader(ContactHeader.NAME);

        String user = ((SipURI) from.getAddress().getURI()).getUser();

        // ตรวจ header Authorization
        AuthorizationHeader authHeader = (AuthorizationHeader) req.getHeader(AuthorizationHeader.NAME);

        if (authHeader == null) {
            // ยังไม่ได้ส่ง Auth -> ให้ตอบ 401 กลับ
            Response resp = messageFactory.createResponse(Response.UNAUTHORIZED, req);
            WWWAuthenticateHeader www = headerFactory.createWWWAuthenticateHeader("Digest");
            www.setRealm("JavaSIPServer");
            www.setNonce(Long.toHexString(System.currentTimeMillis()));
            resp.addHeader(www);
            sendResponse(evt, resp);
            return;
        }

        // แก้ตรงนี้
        String correctPassword = validUsers.get(user);
        String providedPassword = authHeader.getResponse();  // client ส่ง plain text password มา

        if (correctPassword == null || !correctPassword.equals(providedPassword)) {
            sendResponse(evt, Response.FORBIDDEN);
            System.out.println("Authentication failed for user: " + user);
            return;
        }

        System.out.println("Authentication success for user: " + user);

        if (contact != null) {
            registeredContacts.put(user, contact);
            System.out.println("REGISTER OK: " + user + " -> " + contact.getAddress());
        }

        sendResponse(evt, Response.OK);
    }



    private void processInvite(RequestEvent evt) throws Exception {
        Request req = evt.getRequest();
        FromHeader from = (FromHeader) req.getHeader(FromHeader.NAME);
        ToHeader to = (ToHeader) req.getHeader(ToHeader.NAME);

        String targetUser = ((SipURI) to.getAddress().getURI()).getUser();
        ContactHeader contact = registeredContacts.get(targetUser);

        if (contact != null) {
            // ส่ง INVITE ต่อ (Proxy)
            SipURI targetURI = (SipURI) contact.getAddress().getURI();
            Request forward = (Request) req.clone();

            forward.setRequestURI(targetURI);
            ClientTransaction ct = sipProvider.getNewClientTransaction(forward);
            ct.sendRequest();

            System.out.println("Forward INVITE from " + from.getAddress() + " to " + contact.getAddress());
        } else {
            sendResponse(evt, Response.TEMPORARILY_UNAVAILABLE);
        }
    }

    private void processBye(RequestEvent evt) throws Exception {
        System.out.println("BYE received");
        sendResponse(evt, Response.OK);
    }

    /**
     * ส่ง SIP Response อัตโนมัติ (รองรับทั้งสร้างเองและส่งโค้ดสถานะ)
     */
    private void sendResponse(RequestEvent evt, Object responseObj) throws Exception {
        Response response;

        if (responseObj instanceof Integer) {
            // ถ้าส่งมาเป็นเลข (statusCode) -> สร้าง response ใหม่
            int statusCode = (Integer) responseObj;
            response = messageFactory.createResponse(statusCode, evt.getRequest());
        } else if (responseObj instanceof Response) {
            // ถ้าส่งมาเป็น Response ที่สร้างไว้แล้ว -> ใช้อันนั้นเลย
            response = (Response) responseObj;
        } else {
            throw new IllegalArgumentException("Invalid response object type");
        }

        ServerTransaction st = evt.getServerTransaction();
        if (st == null) {
            st = sipProvider.getNewServerTransaction(evt.getRequest());
        }

        st.sendResponse(response);

        System.out.println("Sent Response: " + response.getStatusCode() + " " + response.getReasonPhrase());
    }


    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        System.out.println("Response: " + response.getStatusCode() + " " +
                response.getReasonPhrase());
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        System.out.println("Timeout: " + timeoutEvent.getTimeout());
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        System.out.println("IO Exception: " + exceptionEvent.getHost());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent event) {
        // ignore
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent event) {
        // ignore
    }
}

