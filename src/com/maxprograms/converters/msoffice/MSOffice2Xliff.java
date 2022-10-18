/*******************************************************************************
 * Copyright (c) 2022 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.converters.msoffice;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.Utils;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

public class MSOffice2Xliff {

	private static String inputFile;
	private static String skeletonFile;
	private static String sourceLanguage;
	private static String targetLanguage;
	private static String text = "";
	static boolean inBody = false;

	private static FileOutputStream out;
	private static FileOutputStream skel;
	private static int segnum;
	private static boolean segByElement;
	private static Segmenter segmenter;
	private static String srcEncoding;

	private static Pattern pattern;
	private static Pattern endPattern;

	private MSOffice2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(Map<String, String> params) {
		List<String> result = new ArrayList<>();

		inputFile = params.get("source");
		String xliffFile = params.get("xliff");
		skeletonFile = params.get("skeleton");
		sourceLanguage = params.get("srcLang");
		targetLanguage = params.get("tgtLang");
		String elementSegmentation = params.get("paragraph");
		String initSegmenter = params.get("srxFile");
		srcEncoding = params.get("srcEncoding");
		String catalog = params.get("catalog");

		segnum = 0;

		if (elementSegmentation == null) {
			segByElement = false;
		} else {
			if (elementSegmentation.equals("yes")) {
				segByElement = true;
			} else {
				segByElement = false;
			}
		}
		try {
			if (!segByElement) {
			  segmenter = new Segmenter(initSegmenter, sourceLanguage, new Catalog(catalog));
			}
			SAXBuilder builder = new SAXBuilder();
			Document doc = builder.build(inputFile);
			Element root = doc.getRootElement();
			out = new FileOutputStream(xliffFile);
			skel = new FileOutputStream(skeletonFile);
			writeHeader();
			recurse(root);
			writeOut("    </body>\n  </file>\n</xliff>");
			out.close();
			skel.close();
			result.add(Constants.SUCCESS);
		} catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
			Logger logger = System.getLogger(MSOffice2Xliff.class.getName());
			logger.log(Level.ERROR, "Error converting MS Office file", e);
			result.add(Constants.ERROR);
			result.add(e.getMessage());
		}

		return result;
	}

	private static void writeHeader() throws IOException {
		String tgtLang = "";
		if (targetLanguage != null) {
			tgtLang = "\" target-language=\"" + targetLanguage;
		}

		writeOut("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
		writeOut(
				"<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");
		writeOut("  <file datatype=\"x-office\" original=\"" + Utils.cleanString(inputFile) + "\" source-language=\""
				+ sourceLanguage + tgtLang + "\" tool-id=\"" + Constants.TOOLID + "\">\n");
		writeOut("    <header>\n");
		writeOut("      <skl>\n");
		writeOut("        <external-file href=\"" + Utils.cleanString(skeletonFile) + "\"/>\n");
		writeOut("      </skl>\n");
		writeOut("      <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD + "\" tool-id=\""
				+ Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
		writeOut("    </header>\n");
		writeOut("    <?encoding " + srcEncoding + "?>\n");
		writeOut("    <body>\n");
	}

	private static void writeSegment(String sourceText) throws IOException, SAXException, ParserConfigurationException {
		// replace escaped quotes with extended characters
		sourceText = replaceText(sourceText, "&quot;", "\uE0FF");

		String s = "      <source>" + sourceText + "</source>";
		SAXBuilder b = new SAXBuilder();
		Document d = b.build(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
		Element source = d.getRootElement();
		List<XMLNode> content = source.getContent();
		String start = "";
		String end = "";
		List<Element> tags = source.getChildren("ph");
		if (tags.size() == 1) {
			if (content.get(0).getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = tags.get(0);
				start = e.getText();
				content.remove(0);
				source.setContent(content);
			} else if (content.get(content.size() - 1).getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = tags.get(0);
				end = e.getText();
				content.remove(content.size() - 1);
				source.setContent(content);
			}
		} else if (tags.size() > 1 && content.get(0).getNodeType() == XMLNode.ELEMENT_NODE
				&& content.get(content.size() - 1).getNodeType() == XMLNode.ELEMENT_NODE) {
			// check if it is possible to send
			// initial and trailing tag to skeleton
			Element first = (Element) content.get(0);
			Element last = (Element) content.get(content.size() - 1);
			String test = first.getText() + last.getText();
			if (checkPairs(test)) {
				start = first.getText();
				end = last.getText();
				List<XMLNode> newContent = new ArrayList<>();
				for (int i = 1; i < content.size() - 1; i++) {
					newContent.add(content.get(i));
				}
				source.setContent(newContent);
			}
		}

		writeSkel(replaceText(start, "\uE0FF", "&quot;"));
		if (containsText(source)) {
			List<Element> phs = source.getChildren("ph");
			for (int i = 0; i < phs.size(); i++) {
				phs.get(i).setAttribute("id", "" + (i + 1));
			}
			writeOut("      <trans-unit id=\"" + segnum + "\" xml:space=\"preserve\">\n");
			writeOut("        " + replaceText(source.toString(), "\uE0FF", "&amp;quot;") + "\n");
			writeOut("      </trans-unit>\n");
			writeSkel("%%%" + segnum++ + "%%%\n");
		} else {
			Iterator<XMLNode> i = source.getContent().iterator();
			while (i.hasNext()) {
				XMLNode n = i.next();
				if (n.getNodeType() == XMLNode.TEXT_NODE) {
					writeSkel(Utils.cleanString(replaceText(((TextNode) n).getText(), "\uE0FF", "&quot;")));
				} else {
					Element e = (Element) n;
					writeSkel(replaceText(e.getText(), "\uE0FF", "&quot;"));
				}
			}
		}
		writeSkel(replaceText(end, "\uE0FF", "&quot;"));
	}

	private static String replaceText(String source, String string, String string2) {
		int index = source.indexOf(string);
		while (index != -1) {
			source = source.substring(0, index) + string2 + source.substring(index + string.length());
			index = source.indexOf(string, index + string2.length());
		}
		return source;
	}

	private static boolean checkPairs(String test) {
		String[] parts = test.trim().split("<");
		Stack<String> stack = new Stack<>();
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].length() > 0) {
				String[] subparts = parts[i].split("[\\s]|>");
				if (subparts[0].startsWith("/")) {
					if (stack.isEmpty()) {
						return false;
					}
					String last = stack.pop();
					if (!last.equals(subparts[0].substring(1))) {
						return false;
					}
				} else {
					stack.push(subparts[0]);
				}
			}
		}
		return stack.isEmpty();
	}

	private static boolean containsText(Element source) {
		List<XMLNode> content = source.getContent();
		String string = "";
		Iterator<XMLNode> it = content.iterator();
		while (it.hasNext()) {
			XMLNode n = it.next();
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				string = string + ((TextNode) n).getText();
			}
		}
		if (isNumeric(string.trim())) {
			return false;
		}
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (!(Character.isWhitespace(c) || c == '\u00A0')) {
				return true;
			}
		}
		return false;
	}

	private static boolean isNumeric(String string) {
		return string.matches("[$\u20AC\u00A3]?[\\s]?[\\-]?([0-9]+[\\.,]?([0-9]+)?)+[\\s]?[%\u20AC]?");
	}

	private static void writeOut(String string) throws IOException {
		out.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static void recurse(Element e) throws IOException, SAXException, ParserConfigurationException {
		writeSkel("<" + e.getName());
		List<Attribute> atts = e.getAttributes();
		Iterator<Attribute> at = atts.iterator();
		while (at.hasNext()) {
			Attribute a = at.next();
			writeSkel(" " + a.getName() + "=\"" + cleanAttribute(a.getValue()) + "\"");
		}
		writeSkel(">");

		List<XMLNode> content = e.getContent();
		Iterator<XMLNode> it = content.iterator();
		while (it.hasNext()) {
			XMLNode n = it.next();
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				writeSkel(n.toString());
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element child = (Element) n;
				if (child.getName().equals("Text")) {
					if (!child.getText().trim().isEmpty()) {
						recurseVisioElement(child);
					} else {
						writeSkel(child.toString());
					}
					continue;
				}
				if (child.getName().matches("[a-z]:p") || child.getName().equals("t")) {
					recursePara(child);
				} else {
					recurse(child);
				}
			}
		}
		writeSkel("</" + e.getName() + ">");
	}

	private static void recurseVisioElement(Element e) throws IOException, SAXException, ParserConfigurationException {
		if (!text.isEmpty()) {
			if (segByElement) {
				writeSegment(text);
			} else {
				String[] segs = segmenter.segment(text);
				for (int h = 0; h < segs.length; h++) {
					String seg = segs[h];
					writeSegment(seg);
				}
			}
			text = "";
		}
		// send the opening tag to skeleton
		writeSkel("<" + e.getName());
		List<Attribute> atts = e.getAttributes();
		Iterator<Attribute> ia = atts.iterator();
		while (ia.hasNext()) {
			Attribute a = ia.next();
			writeSkel(" " + a.getName() + "=\"" + cleanAttribute(a.getValue()) + "\"");
		}
		writeSkel(">");

		List<XMLNode> content = e.getContent();
		Iterator<XMLNode> it = content.iterator();
		while (it.hasNext()) {
			XMLNode node = it.next();
			if (node.getNodeType() == XMLNode.TEXT_NODE) {
				TextNode t = (TextNode) node;
				text = text + t.toString();
			}
			if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element child = (Element) node;
				recurseVisioChild(child);
			}
		}

		text = text.replace("</ph><ph>", "");
		if (!text.isEmpty()) {
			if (segByElement) {
				writeSegment(text);
			} else {
				String[] segs = segmenter.segment(text);
				for (int h = 0; h < segs.length; h++) {
					String seg = segs[h];
					writeSegment(seg);
				}
			}
			text = "";
		}
		// send closing tag to skeleton
		writeSkel("</" + e.getName() + ">");
	}

	private static void recurseVisioChild(Element e) {
		text = text + "<ph>&lt;" + e.getName();
		List<Attribute> atts = e.getAttributes();
		Iterator<Attribute> ia = atts.iterator();
		while (ia.hasNext()) {
			Attribute a = ia.next();
			text = text + " " + a.getName() + "=\"" + cleanAttribute(a.getValue()) + "\"";
		}
		text = text + "&gt;</ph>";

		List<XMLNode> content = e.getContent();
		Iterator<XMLNode> it = content.iterator();
		while (it.hasNext()) {
			XMLNode node = it.next();
			if (node.getNodeType() == XMLNode.TEXT_NODE) {
				TextNode t = (TextNode) node;
				text = text + t.toString();
			}
			if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element child = (Element) node;
				recurseVisioChild(child);
			}
		}

		text = text + "<ph>&lt;/" + e.getName() + "&gt;</ph>";
	}

	private static void writeSkel(String string) throws IOException {
		skel.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static void recursePara(Element e) throws IOException, SAXException, ParserConfigurationException {
		if ("w:p".equals(e.getName()) || "a:p".equals(e.getName())) {
			if (!text.isEmpty()) {
				if (segByElement) {
					writeSegment(text);
				} else {
					String[] segs = segmenter.segment(text);
					for (int h = 0; h < segs.length; h++) {
						String seg = segs[h];
						writeSegment(seg);
					}
				}
				text = "";
			}
			cleanPara(e);
		}
		cleanLinks(e);

		// send the opening tag to skeleton
		writeSkel("<" + e.getName());
		List<Attribute> atts = e.getAttributes();
		Iterator<Attribute> ia = atts.iterator();
		while (ia.hasNext()) {
			Attribute a = ia.next();
			writeSkel(" " + a.getName() + "=\"" + cleanAttribute(a.getValue()) + "\"");
		}
		writeSkel(">");

		// send initial elements that don't have text to skeleton
		List<Element> content = e.getChildren();
		int i = 0;
		for (; i < content.size(); i++) {
			Element child = content.get(i);
			if (hasTextElement(child)) {
				break;
			}
			writeSkel(child.toString());
		}
		if (e.getName().matches("[a-z]:t") || e.getName().equals("t")) {
			text = fixHtmlTags(e.getText());
		} else {
			for (; i < content.size(); i++) {
				recursePhrase(content.get(i));
			}
		}
		text = text.replace("</ph><ph>", "");
		if (!text.isEmpty()) {
			if (segByElement) {
				writeSegment(text);
			} else {
				String[] segs = segmenter.segment(text);
				for (int h = 0; h < segs.length; h++) {
					String seg = segs[h];
					writeSegment(seg);
				}
			}
			text = "";
		}
		// send closing tag to skeleton
		writeSkel("</" + e.getName() + ">");
	}

	private static void cleanPara(Element e) {
		List<Element> proofread = e.getChildren("w:proofErr");
		for (int i = 0; i < proofread.size(); i++) {
			e.removeChild(proofread.get(i));
		}
		removeProperties(e, "w:lang");
		removeProperties(e, "w:noProof");
		mergeRegions(e);
	}

	private static void mergeRegions(Element paragraph) {
		int curr = 0;
		while (curr < paragraph.getChildren().size()) {
			List<Element> children = paragraph.getChildren();
			Element currRegion = children.get(curr);
			if (!currRegion.getName().equals("w:r")) {
				curr++;
				continue;
			}
			if (currRegion.getChild("w:t") == null) {
				curr++;
				continue;
			}
			int next = curr + 1;
			boolean merge = true;
			while (next < paragraph.getChildren().size() && merge) {
				Element nextRegion = paragraph.getChildren().get(next);
				if (!nextRegion.getName().equals("w:r")) {
					merge = false;
					continue;
				}
				if (nextRegion.getChild("w:t") == null) {
					merge = false;
					continue;
				}
				Map<String, Element> currProps = buildProps(currRegion);
				Map<String, Element> nextProps = buildProps(nextRegion);
				if (currProps.size() != nextProps.size()) {
					merge = false;
				} else {
					Set<String> keys = currProps.keySet();
					Iterator<String> it = keys.iterator();
					while (it.hasNext()) {
						String key = it.next();
						if (!nextProps.containsKey(key)) {
							merge = false;
							break;
						}
						if (!currProps.get(key).equals(nextProps.get(key))) {
							merge = false;
							break;
						}
					}
					if (merge) {
						currRegion.getChild("w:t").setAttribute("xml:space", "preserve");
						List<Element> content = nextRegion.getChildren();
						for (int i = 0; i < content.size(); i++) {
							Element e = content.get(i);
							if ("w:tab".equals(e.getName()) || "w:t".equals(e.getName())) {
								currRegion.addContent(e);
							}
						}
						paragraph.removeChild(nextRegion);
					}
				}
			}
			curr++;
		}

		List<Element> regions = paragraph.getChildren("w:r");
		for (int i = 0; i < regions.size(); i++) {
			Element region = regions.get(i);
			List<XMLNode> newContent = new ArrayList<>();
			List<XMLNode> oldContent = region.getContent();
			Iterator<XMLNode> it = oldContent.iterator();
			Element last = null;
			while (it.hasNext()) {
				XMLNode node = it.next();
				if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
					Element e = (Element) node;
					if (last == null) {
						last = e;
						newContent.add(node);
					} else {
						if (last.getName().equals(e.getName())) {
							last.addContent(e.getContent());
						} else {
							newContent.add(e);
							last = e;
						}
					}
				} else {
					newContent.add(node);
				}
			}
			region.setContent(newContent);
		}
	}

	private static Map<String, Element> buildProps(Element region) {
		Map<String, Element> result = new HashMap<>();
		Element regionProps = region.getChild("w:rPr");
		if (regionProps != null) {
			Iterator<Element> it = regionProps.getChildren().iterator();
			while (it.hasNext()) {
				Element prop = it.next();
				result.put(prop.getName(), prop);
			}
		}
		return result;
	}

	private static void removeProperties(Element e, String name) {
		List<Element> regions = e.getChildren("w:r");
		Iterator<Element> r = regions.iterator();
		while (r.hasNext()) {
			Element region = r.next();
			List<Element> regionProps = region.getChildren("w:rPr");
			Iterator<Element> it = regionProps.iterator();
			List<Element> remove = new ArrayList<>();
			while (it.hasNext()) {
				Element props = it.next();
				Element prop = props.getChild(name);
				if (prop != null) {
					props.removeChild(prop);
				}
				if (props.getChildren().isEmpty()) {
					remove.add(props);
				}
			}
			for (int i = 0; i < remove.size(); i++) {
				region.removeChild(remove.get(i));
			}
		}
	}

	private static void cleanLinks(Element e) {
		if (e.getName().matches("[a-z]:instrText")) {
			if (e.getText().indexOf("HYPERLINK") != -1) {
				String newLink = e.getText().replace("&", "&amp;");
				e.setText(newLink);
			}
			return;
		}
		List<Element> children = e.getChildren();
		for (int i = 0; i < children.size(); i++) {
			cleanLinks(children.get(i));
		}
	}

	private static boolean hasTextElement(Element e) {
		if (e.getName().matches("[a-z]:t") || e.getName().equals("t")) {
			return true;
		}
		boolean containsText = false;
		List<Element> children = e.getChildren();
		for (int i = 0; i < children.size(); i++) {
			containsText = containsText || hasTextElement(children.get(i));
		}
		return containsText;
	}

	private static boolean isBreak(Element e) {
		return "w:r".equals(e.getName()) && e.getChildren().size() == 1
				&& "w:br".equals(e.getChildren().get(0).getName());
	}

	private static void recursePhrase(Element e) throws IOException, SAXException, ParserConfigurationException {
		if ("w:r".equals(e.getName()) && isBreak(e)) {
			if (!text.isEmpty()) {
				if (segByElement) {
					writeSegment(text);
				} else {
					String[] segs = segmenter.segment(text);
					for (int h = 0; h < segs.length; h++) {
						String seg = segs[h];
						writeSegment(seg);
					}
				}
				text = "";
			}
			writeSkel(e.toString());
			return;
		}
		if (e.getName().equals("w:p")) {
			cleanPara(e);
		}
		text = text + "<ph>&lt;" + e.getName();
		List<Attribute> atts = e.getAttributes();
		Iterator<Attribute> ia = atts.iterator();
		while (ia.hasNext()) {
			Attribute a = ia.next();
			text = text + " " + a.getName() + "=\"" + cleanAttribute(a.getValue()) + "\"";
		}
		text = text + "&gt;</ph>";

		if (e.getName().matches("[a-z]:t") || e.getName().equals("t")) {
			// text = text + Utils.cleanString(e.getText());
			text = text + fixHtmlTags(e.getText());
		} else {
			List<XMLNode> children = e.getContent();
			for (int i = 0; i < children.size(); i++) {
				XMLNode n = children.get(i);
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					Element child = (Element) n;
					if (child.getName().matches("[a-z]:p")) {
						recursePara(child);
					} else {
						recursePhrase(child);
					}
				}
				if (n.getNodeType() == XMLNode.TEXT_NODE) {
					text = text + "<ph>" + Utils.cleanString(n.toString()) + "</ph>";
				}
			}
		}
		text = text + "<ph>&lt;/" + e.getName() + " &gt;</ph>";
	}

	private static String cleanAttribute(String value) {
		value = value.replace("&", "&amp;amp;");
		value = value.replace("<", "&amp;lt;");
		value = value.replace(">", "&amp;gt;");
		value = value.replace("\"", "&quot;");
		return value;
	}

	private static String fixHtmlTags(String original) {
		if (pattern == null) {
			pattern = Pattern.compile("&lt;[A-Za-z0-9]+([\\s][A-Za-z\\-\\.]+=[\"|\'][^<&>]*[\"|\'])*[\\s]*/?&gt;");
		}
		if (endPattern == null) {
			endPattern = Pattern.compile("&lt;/[A-Za-z0-9]+&gt;");
		}
		Element src = new Element("src");
		src.setText(Utils.cleanString(original));
		String e = normalise(src.getText());

		Matcher matcher = pattern.matcher(e);
		if (matcher.find()) {
			List<XMLNode> newContent = new Vector<>();
			List<XMLNode> content = src.getContent();
			Iterator<XMLNode> it = content.iterator();
			while (it.hasNext()) {
				XMLNode node = it.next();
				if (node.getNodeType() == XMLNode.TEXT_NODE) {
					TextNode t = (TextNode) node;
					String nodeText = normalise(t.getText());
					matcher = pattern.matcher(nodeText);
					if (matcher.find()) {
						matcher.reset();
						while (matcher.find()) {
							int start = matcher.start();
							int end = matcher.end();

							String s = nodeText.substring(0, start);
							if (!s.isEmpty()) {
								newContent.add(new TextNode(s));
							}
							String tag = nodeText.substring(start, end);
							Element ph = new Element("ph");
							ph.setText(tag);
							newContent.add(ph);

							nodeText = nodeText.substring(end);
							matcher = pattern.matcher(nodeText);
						}
						if (!nodeText.isEmpty()) {
							newContent.add(new TextNode(nodeText));
						}
					} else {
						if (!((TextNode) node).getText().isEmpty()) {
							newContent.add(node);
						}
					}
				} else {
					newContent.add(node);
				}
			}
			src.setContent(newContent);
		}
		matcher = endPattern.matcher(e);
		if (matcher.find()) {
			List<XMLNode> newContent = new Vector<>();
			List<XMLNode> content = src.getContent();
			Iterator<XMLNode> it = content.iterator();
			while (it.hasNext()) {
				XMLNode node = it.next();
				if (node.getNodeType() == XMLNode.TEXT_NODE) {
					TextNode t = (TextNode) node;
					String text = normalise(t.getText());
					matcher = endPattern.matcher(text);
					if (matcher.find()) {
						matcher.reset();
						while (matcher.find()) {
							int start = matcher.start();
							int end = matcher.end();

							String s = text.substring(0, start);
							if (!s.isEmpty()) {
								newContent.add(new TextNode(s));
							}

							String tag = text.substring(start, end);
							Element ph = new Element("ph");
							ph.setText(tag);
							newContent.add(ph);

							text = text.substring(end);
							matcher = endPattern.matcher(text);
						}
						if (!text.isEmpty()) {
							newContent.add(new TextNode(text));
						}
					} else {
						if (!((TextNode) node).getText().isEmpty()) {
							newContent.add(node);
						}
					}
				} else {
					newContent.add(node);
				}
			}
			src.setContent(newContent);
		}
		if (src.getChildren().isEmpty()) {
			return Utils.cleanString(original);
		}
		StringBuilder sb = new StringBuilder();
		List<XMLNode> content = src.getContent();
		Iterator<XMLNode> it = content.iterator();
		while (it.hasNext()) {
			sb.append(it.next().toString());
		}
		return sb.toString();
	}

	private static String normalise(String string) {
		String result = string;
		result = result.replace('\n', ' ');
		result = result.replaceAll("\\s(\\s)+", " ");
		return result;
	}
}
