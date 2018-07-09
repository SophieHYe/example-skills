package iristk.app.Houndify;

import java.io.File;

import org.slf4j.Logger;

import com.Hound.HoundJSON.ConversationStateJSON;
import com.amazonaws.services.xray.model.Http;

import iristk.cfg.ParseResult;
import iristk.cfg.Parser;
import iristk.cfg.SRGSGrammar;
import iristk.furhat.Queryable;
import iristk.furhat.skill.FlowResource;
import iristk.furhat.QueryResponse;
import iristk.furhat.skill.Skill;
import iristk.furhat.skill.SkillHandler;
import iristk.furhat.skill.TextFileResource;
import iristk.furhat.skill.XmlResource;
import iristk.speech.OpenVocabularyContext;
import iristk.speech.SemanticGrammarContext;
import iristk.speech.SpeechGrammarContext;
import iristk.system.IrisUtils;
import iristk.util.Language;
import iristk.util.Record;

public class HoundifySkill extends Skill {
		
	private static final String RECOGNIZER_GRAMMAR = "grammar";
	private static final String RECOGNIZER_OPEN = "open";

	private HoundifyFlow flow;
	private static Logger logger = IrisUtils.getLogger(HoundifySkill.class); 
	
	private File propertiesFile;
	private String name = "HoundifySkill";
	private Language language = Language.ENGLISH_US;
	private String recognizer = "open";
	
	private File houndifyCredentialsFile;
	private HoundifyClient houndifyClient;
	private ConversationStateJSON conversationState;

	private String houndify_client_id;
    private String houndify_client_key;
    private String houndify_user_id;	
    private Double location_lon;
    private Double location_lat;
    private String location_city;
    private String location_state;
    private String location_country;
    
	private SRGSGrammar entryGrammar;
	private Parser entryParser;
	private Record querySemantics; 
	
	public HoundifySkill() {
		
		propertiesFile = getPackageFile("skill.properties");
		houndifyCredentialsFile = getPackageFile("houndify_credentials.properties");
		
		addResource(new TextFileResource(this, "Properties", propertiesFile));
		addResource(new TextFileResource(this, "Credentials", houndifyCredentialsFile));
		addResource(new FlowResource(this, "Flow", getSrcFile("HoundifyFlow.xml")));
		addResource(new XmlResource(this, "Grammar", getPackageFile("HoundifyGrammar.xml")));
		
		try {
			Record config = Record.fromProperties(propertiesFile);
			name = config.getString("name", name);
			language = new Language(config.getString("language", language.getCode()));
			recognizer = config.getString("recognizer", recognizer);
			
			Record credentials = Record.fromProperties(houndifyCredentialsFile);
			houndify_client_id = credentials.getString("houndify_client_id");
			houndify_client_key = credentials.getString("houndify_client_key");
			houndify_user_id = credentials.getString("houndify_user_id");
			location_lon = credentials.getDouble("location_lon");
			location_lat = credentials.getDouble("location_lat");
			location_city = credentials.getString("location_city");
			location_state = credentials.getString("location_state");
			location_country = credentials.getString("location_country");
			entryGrammar = new SRGSGrammar(getPackageFile("EntryGrammar.xml"));
			
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		getRequirements().setLanguage(language);
		getRequirements().setSpeechGrammar(recognizer.equals(RECOGNIZER_GRAMMAR));
		getRequirements().setOpenVocabulary(recognizer.equals(RECOGNIZER_OPEN));

		addEntriesFromFlow(HoundifyFlow.class, () -> flow);
		
		if (!houndify_client_id.equals("") && !houndify_client_key.equals("") && !houndify_user_id.equals("")) {
			houndifyClient = new HoundifyClient(
					houndify_client_id, 
					houndify_client_key, 
					houndify_user_id, 
					location_lon, 
					location_lat,
					location_city,
					location_state,
					location_country
					);
		}
    	
		entryParser = new Parser();
        entryParser.loadGrammar("entry", entryGrammar);
        entryParser.activateGrammar("entry");
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	/*
	 *	Method that is run on skill start. Sets up the recognizer contexts and 
	 *	creates a new dialogue flow and inserts a houndify flow client to it so 
	 *	that we can query houndify from the flow 
	 * 
	 */
	@Override
	public void init() throws Exception {
		SkillHandler handler = getSkillHandler();
		
		handler.loadContext("default", new OpenVocabularyContext(language));
		handler.loadContext("default", new SemanticGrammarContext(new SRGSGrammar(getPackageFile("HoundifyGrammar.xml"))));
		handler.setDefaultContext("default");

		flow = new HoundifyFlow(handler.getSystemAgentFlow(), new HoundifyFlowClient());
	}

	@Override
	public void stop() throws Exception {
	}
	
	
	/*
	 * Mini class used to query houndify from the flow 
	 * 
	 */
	public class HoundifyFlowClient {
		public String answer(String question) {
			// Try to identify questions using our EntryGrammar
			ParseResult result = entryParser.parse(question);
			if (result.getSemCoverage() == 0 || houndifyClient == null) {
				return "";			
		    }
			
			// Query Houndify
			Record answerRecord = houndifyClient.query(question, conversationState);
			
			// Save conversationState if returned (houndify specific)
			if (!answerRecord.empty()) {
				if (answerRecord.has("conversationState")){
					conversationState = (ConversationStateJSON) answerRecord.get("conversationState");				
				}
				return answerRecord.getString("answer");
			}
			return "";
		}
	}
	
	

}
