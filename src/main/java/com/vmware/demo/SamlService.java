package com.vmware.demo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.joda.time.DateTime;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLVersion;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml2.core.AuthnContextComparisonTypeEnumeration;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.NameIDPolicy;
import org.opensaml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.impl.AuthnContextClassRefBuilder;
import org.opensaml.saml2.core.impl.AuthnRequestBuilder;
import org.opensaml.saml2.core.impl.IssuerBuilder;
import org.opensaml.saml2.core.impl.NameIDPolicyBuilder;
import org.opensaml.saml2.core.impl.RequestedAuthnContextBuilder;
import org.opensaml.saml2.encryption.Decrypter;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml2.metadata.provider.HTTPMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.encryption.DecryptionException;
import org.opensaml.xml.encryption.InlineEncryptedKeyResolver;
import org.opensaml.xml.io.Marshaller;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.security.keyinfo.StaticKeyInfoCredentialResolver;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.signature.X509Data;
import org.opensaml.xml.util.Base64;
import org.opensaml.xml.util.XMLHelper;
import org.opensaml.xml.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.vmware.demo.db.entity.IdentityProvider;

public class SamlService {
	private static final Logger logger = LoggerFactory.getLogger(SamlService.class);
//	private String redirectionUrl = "http://localhost:9080/ServiceProvider/";
	private String issuerString = "http://localhost:9080/ServiceProvider/";
	private boolean bootstrap = false;
	private static SamlService INSTANCE = null;

	private SamlService() {
		init();
	}
	
	public static SamlService getInstance() {
		if (null == INSTANCE) {
			INSTANCE = new SamlService();
		}
		return INSTANCE;
	}
	
	private void init() {
        // Bootstrap the OpenSAML libraries
        if (bootstrap  == false) {
            try {
				DefaultBootstrap.bootstrap();
	            bootstrap = true;
			} catch (ConfigurationException e) {
				logger.error("Failed to bootstrap OpenSAML", e);
			}
            
            // Add Bouncy Castle provider for x509 parsing
            Security.addProvider(new BouncyCastleProvider());
        }
        
	}

	public String generateSAMLRequest(String assertionConsumerServiceURL, String nameIdFormat) {
		String samlRequest = "";

		try {
			// Generate ID
			String randId = "A71AB3E13";

			// Create an issuer Object
			IssuerBuilder issuerBuilder = new IssuerBuilder();
			Issuer issuer = issuerBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:assertion", "Issuer", "samlp");
			issuer.setValue(issuerString);

			// Create NameIDPolicy
			NameIDPolicyBuilder nameIdPolicyBuilder = new NameIDPolicyBuilder();
			NameIDPolicy nameIdPolicy = nameIdPolicyBuilder.buildObject();
			if (StringUtils.isNotEmpty(nameIdFormat)) {
				nameIdPolicy.setFormat(nameIdFormat);
			}
			nameIdPolicy.setSPNameQualifier(issuerString);
			nameIdPolicy.setAllowCreate(true);

			// Create AuthnContextClassRef
			AuthnContextClassRefBuilder authnContextClassRefBuilder = new AuthnContextClassRefBuilder();
			AuthnContextClassRef authnContextClassRef = authnContextClassRefBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:assertion", "AuthnContextClassRef", "saml");
			authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");
			
			// Create RequestedAuthnContext
			RequestedAuthnContextBuilder requestedAuthnContextBuilder = new RequestedAuthnContextBuilder();
			RequestedAuthnContext requestedAuthnContext = requestedAuthnContextBuilder.buildObject();
			requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.EXACT);
			requestedAuthnContext.getAuthnContextClassRefs().add(authnContextClassRef);

			AuthnRequestBuilder authRequestBuilder = new AuthnRequestBuilder();
			AuthnRequest authRequest = authRequestBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:protocol", "AuthnRequest", "samlp");
			authRequest.setForceAuthn(false);
			authRequest.setIsPassive(false);
			authRequest.setIssueInstant(new DateTime());
			authRequest.setProtocolBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
			authRequest.setAssertionConsumerServiceURL(assertionConsumerServiceURL);
			authRequest.setIssuer(issuer);
			authRequest.setNameIDPolicy(nameIdPolicy);
			authRequest.setRequestedAuthnContext(requestedAuthnContext);
			authRequest.setID(randId);
			authRequest.setVersion(SAMLVersion.VERSION_20);
			String stringRep = authRequest.toString();

			Marshaller marshaller = org.opensaml.Configuration.getMarshallerFactory().getMarshaller(authRequest);
			org.w3c.dom.Element authDOM = marshaller.marshall(authRequest);
			StringWriter rspWrt = new StringWriter();
			XMLHelper.writeNode(authDOM, rspWrt);
			String messageXML = rspWrt.toString();
			Deflater deflater = new Deflater(Deflater.DEFLATED, true);
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(byteArrayOutputStream, deflater);
			deflaterOutputStream.write(messageXML.getBytes());
			deflaterOutputStream.close();
			samlRequest = Base64.encodeBytes(byteArrayOutputStream.toByteArray(), Base64.DONT_BREAK_LINES);
			//samlRequest = URLEncoder.encode(samlRequest);

			logger.info("samlRequest: " + samlRequest);

		} catch (MarshallingException e) {
			logger.error("General Error", e);
		} catch (IOException e) {
			logger.error("General Error", e);
		}
		return samlRequest;
	}

	public String decodeSAMLResponse(String samlResponse) {
		// Decode base64 assertion
		byte[] decodedBytes = Base64.decode(samlResponse);
		return new String(decodedBytes);
	}
	
	public String validateSAMLResponse(String samlResponse, String samlCert) throws Exception {
		String decodedString = "";
		try {
			decodedString = decodeSAMLResponse(samlResponse);
			InputStream inputStream = new ByteArrayInputStream(decodedString.getBytes("UTF-8"));

			// Parse XML
			BasicParserPool parserPoolManager = new BasicParserPool();
			parserPoolManager.setNamespaceAware(true);
			parserPoolManager.setIgnoreElementContentWhitespace(true);
			Document document = parserPoolManager.parse(inputStream);
			Element metadataRoot = document.getDocumentElement();

			QName qName = new QName(metadataRoot.getNamespaceURI(), metadataRoot.getLocalName(), metadataRoot.getPrefix());

			// Unmarshall document
			Unmarshaller unmarshaller = Configuration.getUnmarshallerFactory().getUnmarshaller(qName);
			Response response = (Response) unmarshaller.unmarshall(metadataRoot);
			Issuer issuer = response.getIssuer();
			logger.info("Parsed response.  Issued:" + response.getIssueInstant().toString() + ", issuer: " + issuer.getValue());
			
	        java.security.cert.X509Certificate jX509Cert = SamlUtils.parsePemCertificate(samlCert);
	        if (null == jX509Cert) {
				logger.info("Failed to parse cert. "+samlCert);
	        	return "";
	        }

	        PublicKey publicCert = jX509Cert.getPublicKey();
			logger.info("Extracted cert.  Cert:" + publicCert);
	        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicCert.getEncoded());
	        
			
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
			logger.debug("Key created by provider: " + keyFactory.getProvider().toString());

			// Setup validation
			BasicX509Credential publicCredential = new BasicX509Credential();
			publicCredential.setPublicKey(publicKey);
			SignatureValidator signatureValidator = new SignatureValidator(publicCredential);
			Signature signature = response.getSignature();

			// Validate
			try {
				signatureValidator.validate(signature);
				logger.info("Assertion signature validated.");
			} catch (ValidationException e) {
				logger.error("Failed to validate signature of assertion", e);
				throw e;
			}

			// Get decryption key
			RSAPrivateKey privateKey = null;
			BasicX509Credential decryptionCredential = new BasicX509Credential();
			decryptionCredential.setPrivateKey(privateKey);
			StaticKeyInfoCredentialResolver skicr = new StaticKeyInfoCredentialResolver(decryptionCredential);

			// Decrypt assertion
			Decrypter decrypter = new Decrypter(null, skicr, new InlineEncryptedKeyResolver());
			if (response.getEncryptedAssertions().isEmpty()) {
				logger.info("Nothing to decrypt in assertion.");
			} else {
				Assertion decryptedAssertion;
				try {
						decryptedAssertion = decrypter.decrypt(response.getEncryptedAssertions().get(0));
						logger.info("Assertion decryption succeeded.");
				} catch (DecryptionException e) {
					logger.error("Failed to decrypt assertion", e);
					throw e;
				}
	
				// Extract attributes, log in output
				List<AttributeStatement> attributeStatements = decryptedAssertion.getAttributeStatements();
				for (int i = 0; i < attributeStatements.size(); i++) {
					List<Attribute> attributes = attributeStatements.get(i).getAttributes();
					for (int x = 0; x < attributes.size(); x++) {
						String strAttributeName = attributes.get(x).getDOM().getAttribute("Name");
	
						List<XMLObject> attributeValues = attributes.get(x).getAttributeValues();
						for (int y = 0; y < attributeValues.size(); y++) {
							String strAttributeValue = attributeValues.get(y).getDOM().getTextContent();
							logger.info(strAttributeName + " = " + strAttributeValue);
						}
					}
				}
			}
		} catch (Exception ex) {
			logger.error("Failed to validate assertion", ex);
			throw ex;
		}
		return decodedString;
	}

	public String loadSigningKeyFromMetaData(String metadataUri) {
		try {
			HTTPMetadataProvider mdp = new HTTPMetadataProvider(metadataUri, 5000);
			mdp.setRequireValidMetadata(true);
			mdp.setParserPool(new BasicParserPool() );
			mdp.initialize();
			
			EntityDescriptor idpEntityDescriptor = mdp.getEntityDescriptor(metadataUri);
			logger.info("Loaded IDP entity descriptor: " + idpEntityDescriptor.getEntityID());
			
			for (KeyDescriptor key : idpEntityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS).getKeyDescriptors()) {
				X509Data x509 = key.getKeyInfo().getX509Datas().get(0);
				org.opensaml.xml.signature.X509Certificate x509Cert = x509.getX509Certificates().get(0);
				logger.info("Found key: "+ x509Cert.getValue().toString());
				return SamlUtils.convertCertToPemFormat(x509Cert.getValue());
			}
		} catch (MetadataProviderException e) {
			logger.error("Failed to load metadata", e);
		}
		return null;
	}

	public String validateSAMLResponse(String sAMLResponse, List<IdentityProvider> identityProviders) throws SamlException {
		String samlResponse = "";
		StringBuilder samlErrors = new StringBuilder();
		for(IdentityProvider identityProvider : identityProviders) {
			try {
				samlResponse  = validateSAMLResponse(sAMLResponse, SamlUtils.extractCert(identityProvider.getMetaData()));
				if (samlResponse.length()>0) {
					return samlResponse;
				}
			} catch (Exception e) {
				samlErrors.append(identityProvider.getHorizonUrl());
				samlErrors.append(":");
				samlErrors.append(e.getLocalizedMessage());
				samlErrors.append("\n");
			}
		}
		throw new SamlException("Cannot validate against any IDP \n("+samlErrors.toString()+")");
	}
}
