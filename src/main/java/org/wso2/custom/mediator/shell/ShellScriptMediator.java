package org.wso2.custom.mediator.shell;

import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Class building a custom shell script mediator for wso2.
 * @author Nicolas Berthel
 */
public class ShellScriptMediator extends AbstractMediator {

	private Log log = LogFactory.getLog(ShellScriptMediator.class);

	/**
	 * Build the custom mediator.
	 */
	public boolean mediate(MessageContext context) {
				
		// Take the script param and names from the context 
		// it is not yet possible to inject dynamic parameters to custom class mediators 
		if (context.getProperty("scriptname") == null) {
			return false;
		}
		
		String scriptparam = "";
		String scriptname = context.getProperty("scriptname").toString();

		
		if (context.getProperty("scriptname") != null) {
			scriptparam = context.getProperty("scriptparam").toString();
		}
		 
		String cmd = "sh " + scriptname + " " + scriptparam;
		ShellExecutor executor = new ShellExecutor(cmd);
		String result = executor.execute();

		log.info(result);
		try {
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
			context.setEnvelope(env);

		} catch (AxisFault af) {
			log.error(af);
		}

		return true;

	}
}
