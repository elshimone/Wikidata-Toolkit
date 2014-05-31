package org.wikidata.wdtk.dumpfiles.processor;

/*
 * #%L
 * Wikidata Toolkit Examples
 * %%
 * Copyright (C) 2014 Wikidata Toolkit Developers
 * %%
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
 * #L%
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.implementation.DataObjectFactoryImpl;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.dumpfiles.DumpContentType;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;
import org.wikidata.wdtk.dumpfiles.constraint.Constraint;
import org.wikidata.wdtk.dumpfiles.parser.constraint.ConstraintMainParser;
import org.wikidata.wdtk.dumpfiles.parser.constraint.ConstraintParserConstant;
import org.wikidata.wdtk.dumpfiles.parser.template.Template;
import org.wikidata.wdtk.dumpfiles.renderer.constraint.ConstraintMainRenderer;
import org.wikidata.wdtk.dumpfiles.renderer.format.Owl2FunctionalRendererFormat;
import org.wikidata.wdtk.dumpfiles.renderer.format.RdfRendererFormat;
import org.wikidata.wdtk.dumpfiles.renderer.format.RendererFormat;

/**
 * 
 * @author Julian Mendez
 * 
 */
public class PropertyConstraintDumpProcessor {

	static final Logger logger = LoggerFactory
			.getLogger(PropertyConstraintDumpProcessor.class);

	static final ValueFactory factory = ValueFactoryImpl.getInstance();

	public static final String DEFAULT_DUMP_DATE = "20140420";
	public static final String DEFAULT_FILE_NAME = "constraints";
	public static final String OWL_FILE_EXTENSION = ".owl";
	public static final String RDF_FILE_EXTENSION = ".rdf";
	public static final String WIKIDATAWIKI = "wikidatawiki";

	public static void main(String[] args) throws IOException {
		(new PropertyConstraintDumpProcessor()).run(args);
	}

	public String escapeChars(String str) {
		return str.replaceAll("&", "&amp;").replaceAll("\"", "&quot;")
				.replaceAll("<", "&lt;").replaceAll("'", "&apos;")
				.replaceAll("\n", "  ");
	}

	private List<Template> getConstraintTemplates(List<Template> list) {
		ConstraintMainParser mainParser = new ConstraintMainParser();
		List<Template> ret = new ArrayList<Template>();
		for (Template template : list) {
			String templateId = mainParser.normalize(template.getId());
			String prefix = mainParser
					.normalize(ConstraintParserConstant.T_CONSTRAINT);
			if (templateId.startsWith(prefix)) {
				ret.add(template);
			}
		}
		return ret;
	}

	public void processAnnotationsOfConstraintTemplates(
			Map<String, List<Template>> templateMap,
			RendererFormat rendererFormat) throws IOException {

		DataObjectFactoryImpl dataObjectFactory = new DataObjectFactoryImpl();

		for (String key : templateMap.keySet()) {
			try {
				List<Template> templates = getConstraintTemplates(templateMap
						.get(key));
				PropertyIdValue property = dataObjectFactory
						.getPropertyIdValue(key.toUpperCase(),
								ConstraintMainParser.DEFAULT_BASE_IRI);
				URI propertyUri = factory.createURI(property.getIri());
				rendererFormat.addAnnotationAssertionComment(propertyUri,
						escapeChars(templates.toString()));
			} catch (Exception e) {
				System.out
						.println("Exception while rendering annotation assertion for '"
								+ key + "'.");
				e.printStackTrace();

			}
		}
	}

	public void processDumps(RendererFormat rendererFormat) throws IOException {
		DumpProcessingController controller = new DumpProcessingController(
				WIKIDATAWIKI);

		// set offline mode true to read only offline dumps
		// controller.setOfflineMode(true);

		PropertyTalkTemplateMwRevisionProcessor propertyTalkTemplateProcessor = new PropertyTalkTemplateMwRevisionProcessor();
		controller.registerMwRevisionProcessor(propertyTalkTemplateProcessor,
				null, true);

		controller.processAllDumps(DumpContentType.CURRENT, DEFAULT_DUMP_DATE,
				DEFAULT_DUMP_DATE);

		rendererFormat.start();
		processAnnotationsOfConstraintTemplates(
				propertyTalkTemplateProcessor.getMap(), rendererFormat);
		processTemplates(propertyTalkTemplateProcessor.getMap(), rendererFormat);
		rendererFormat.finish();
	}

	public void processTemplates(Map<String, List<Template>> templateMap,
			RendererFormat rendererFormat) throws IOException {
		ConstraintMainParser parser = new ConstraintMainParser();
		ConstraintMainRenderer renderer = new ConstraintMainRenderer(
				rendererFormat);
		for (String key : templateMap.keySet()) {
			List<Template> templates = templateMap.get(key);
			for (Template template : templates) {

				Constraint constraint = null;
				try {
					constraint = parser.parse(template);
				} catch (Exception e) {
					System.out.println("Exception while parsing " + key);
					System.out.println("Template: " + template.toString());
					e.printStackTrace();
				}

				try {
					if (constraint != null) {
						constraint.accept(renderer);
					}
				} catch (Exception e) {
					System.out.println("Exception while rendering " + key);
					System.out.println("Template: " + template.toString());
					System.out.println("Constraint: " + constraint.toString());
					e.printStackTrace();
				}
			}
		}
	}

	public void storeOwl(File file) throws IOException {
		FileWriter output = new FileWriter(file);
		Owl2FunctionalRendererFormat rendererFormat = new Owl2FunctionalRendererFormat(
				output);
		processDumps(rendererFormat);
		output.flush();
		output.close();
	}

	public void storeRdf(File file) throws IOException {
		FileOutputStream output = new FileOutputStream(file);
		RdfRendererFormat rendererFormat = new RdfRendererFormat(output);
		processDumps(rendererFormat);
		output.flush();
		output.close();
	}

	public void run(String[] args) throws IOException {
		ExampleHelpers.configureLogging();
		String fileName = DEFAULT_FILE_NAME;
		if (args.length > 0) {
			fileName = args[0];
		}
		try {
			storeOwl(new File(fileName + OWL_FILE_EXTENSION));
		} catch (Exception e) {
			System.out.println("Exception while rendering OWL 2 Functional.");
			e.printStackTrace();
		}
		try {
			storeRdf(new File(fileName + RDF_FILE_EXTENSION));
		} catch (Exception e) {
			System.out.println("Exception while rending RDF.");
			e.printStackTrace();
		}
	}

}