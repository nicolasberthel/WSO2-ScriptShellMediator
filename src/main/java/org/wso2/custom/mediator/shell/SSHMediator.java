package org.wso2.custom.mediator.shell;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.mediators.AbstractMediator;

import com.jcraft.jsch.Channel; 
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;


/**
 * Custom WSO2 mediator allowing to send remote commands via ssh.
 */
public class SSHMediator extends AbstractMediator implements ManagedLifecycle {

	private Log log = LogFactory.getLog(SSHMediator.class);

	private String blocking = "false";
	private String host;
	private String user;
	private String key;
	
	/* maximum number of connection retry */
	private int maxRetry = 5;
	/* time between 2 connection retry in ms */
	private int waitBeforeRetry = 5000;
	
	private JSch jsch;
	private Session session;

	/**
	 * Build the custom mediator.
	 */
	public boolean mediate(MessageContext context) {

		// Take the script name from the context
		// it is not yet possible to inject dynamic parameters to custom class mediators
		if (context.getProperty("script") == null) {
			return false;
		}

		try {
			int tryCount = 0;
			String result = "retry";
			while (result.equalsIgnoreCase("retry")) {
				tryCount++;
				result = this.sendCommand(context.getProperty("script").toString(), context, tryCount);
				if (result.equalsIgnoreCase("retry")) {
					// Wait some seconds before retry
					Thread.sleep(waitBeforeRetry);
				}
			}
			
			context.setEnvelope(buildResponseEnvelop(result, context));
			
		} catch (AxisFault e) {
			handleException(e.getMessage(), context);
		} catch (IOException e) {
			handleException(e.getMessage(), context);
		} catch (InterruptedException e) {
			handleException(e.getMessage(), context);
		}
		return true;
	}
	
	/**
	 * Build the result envelop containing the script output.
	 * @param result	Job result as a string				
	 * @param context	Message Context	
	 * @return			Envelop to store in the context
	 */
	private SOAPEnvelope buildResponseEnvelop(String result, MessageContext context) {
		SOAPFactory fac;
		if (context.isSOAP11()) {
			fac = OMAbstractFactory.getSOAP11Factory();
		} else {
			fac = OMAbstractFactory.getSOAP12Factory();
		}
		context.getEnvelope().buildWithAttachments();
		SOAPEnvelope env = fac.createSOAPEnvelope();
		fac.createSOAPBody(env);
		OMElement newElement = fac.createOMElement(new QName("stdout"), env.getBody());
		newElement.setText(result);
		return env;
	}
	
	private void openSession(MessageContext context) {
		java.util.Properties config = new java.util.Properties();
		try {
			if (key != null && !key.trim().equals("")) {
				jsch.addIdentity(key);
				config.put("StrictHostKeyChecking", "no");
			}
	
			session = jsch.getSession(user, host, 22);
			session.setConfig(config);
			session.connect(60000);
		} catch (JSchException ex) {
			if (context != null) {
				handleException("Error when establishing SSH Connection to " + host, context);
			} else {
				log.error("Error when establishing SSH Connection to " + host, ex);
			}
		}
	}
	
	/**
	 * Connect to remote host and execute a command.
	 * @param command					Command to launch remotely
	 * @return							Script result as a string
	 * @throws IOException				Exception when reading the response
	 * @throws InterruptedException		Exception when adding a timer
	 */
	private String sendCommand(String command, MessageContext context, int tryCount) throws IOException, InterruptedException {
		try {
			Channel channel = session.openChannel("exec");
			InputStream in = channel.getInputStream();
			
			((ChannelExec) channel).setCommand(command);
			((ChannelExec) channel).setErrStream(System.err);
	
			channel.connect();
			String result = "";
	
			/* Only collect the result if the script in called in a synchronous way */
			if (blocking.equalsIgnoreCase("true")) {
				StringBuilder line = new StringBuilder();
				char toAppend = ' ';
				while (true) {
					while (in.available() > 0) {
						toAppend = (char) in.read();
						if (toAppend == '\n') {
							result += line.toString();
							line.setLength(0);
						} else
							line.append(toAppend);
					}
					if (channel.isClosed()) {
						if (in.available() > 0)
							continue;
						break;
					}
					Thread.sleep(1000);
				}
			}
			channel.disconnect();
			return result;
		} catch (JSchException ex) {
			/* In case of error we can retry */
			if (tryCount <= maxRetry) {
				log.error("SSH Connection error try #" + tryCount + " on " + maxRetry);
				if (tryCount <= 1) {
					/* For the first try we retry to connect ssh first */
					openSession(context);
				}
				return "retry";
			} else {
				/* If maximum of retry reached we fail. */
				handleException("Error when trying to connect to SSH", ex, context);
				return "";
			}
		}
	}

	public void setBlocking(String newValue) {
		blocking = newValue;
	}

	public String getBlocking() {
		return blocking;
	}
	
	public void setHost(String newHost) {
		this.host = newHost;
	}
	public String getHost() {
		return this.host;
	}
	
	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public void destroy() {
		this.session.disconnect();
	}

	public void init(SynapseEnvironment arg0) {
		jsch = new JSch();
		this.openSession(null);
	}
}
