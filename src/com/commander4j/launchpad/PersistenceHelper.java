package com.commander4j.launchpad;

/*******************************************************************************
 * Title:        Commander4j
 * Description:  XML Persistence for LaunchPad
 *               - Tabs wrapped in JScrollPane (vertical scrollbar)
 *               - Saves/loads selected tab (selected="true")
 * Author:       Dave (with ChatGPT assistance)
 * License:      GNU General Public License
 *******************************************************************************/

import java.io.File;
import java.io.FileOutputStream;

import javax.swing.JTabbedPane;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class PersistenceHelper
{
    private static final String CONFIG_PATH = "./xml/config/launchpad.xml";

    public static void saveState(JTabbedPane tabs)
    {
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.newDocument();

            Element root = doc.createElement("launchpad");
            doc.appendChild(root);

            int selected = tabs.getSelectedIndex();

            for (int t = 0; t < tabs.getTabCount(); t++) {
                Element tabEl = doc.createElement("tab");
                tabEl.setAttribute("name", tabs.getTitleAt(t));
                if (t == selected) {
                    tabEl.setAttribute("selected", "true"); // remember currently selected tab
                }
                root.appendChild(tabEl);

                // unwrap LaunchTabPanel if it’s inside a JScrollPane
                LaunchTabPanel panel = null;
                var comp = tabs.getComponentAt(t);
                if (comp instanceof LaunchTabPanel p) {
                    panel = p;
                } else if (comp instanceof JScrollPane sp) {
                    var view = (sp.getViewport() != null) ? sp.getViewport().getView() : null;
                    if (view instanceof LaunchTabPanel p) panel = p;
                }

                if (panel != null) {
                    for (int c = 0; c < panel.getComponentCount(); c++) {
                        if (panel.getComponent(c) instanceof LaunchCell cell) {
                            AppComponent app = cell.getApp();
                            if (app != null) {
                                Element cellEl = doc.createElement("cell");
                                cellEl.setAttribute("index", String.valueOf(c));
                                cellEl.setAttribute("path", app.getAppPath());

                                String customIcon = app.getCustomIconPath();
                                if (customIcon != null && !customIcon.isBlank()) {
                                    cellEl.setAttribute("icon", customIcon);
                                }
                                tabEl.appendChild(cellEl);
                            }
                        }
                    }
                }
            }

            File outFile = new File(CONFIG_PATH);
            outFile.getParentFile().mkdirs();

            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.transform(new DOMSource(doc), new StreamResult(new FileOutputStream(outFile)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadState(JTabbedPane tabs)
    {
        try {
            File inFile = new File(CONFIG_PATH);
            if (!inFile.exists()) return;

            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(inFile);

            tabs.removeAll();

            int selectedIndex = -1;

            var tabNodes = doc.getElementsByTagName("tab");
            for (int i = 0; i < tabNodes.getLength(); i++) {
                Element tabEl = (Element) tabNodes.item(i);
                String name = tabEl.getAttribute("name");
                boolean isSelected = "true".equalsIgnoreCase(tabEl.getAttribute("selected"));

                // Create panel and wrap it in a vertical-only scroller
                LaunchTabPanel panel = new LaunchTabPanel();
                JScrollPane sp = new JScrollPane(
                    panel,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                );
                sp.setBorder(null);
                sp.getVerticalScrollBar().setUnitIncrement(24);

                tabs.addTab(name, sp);
                int thisIndex = tabs.getTabCount() - 1;
                if (isSelected) selectedIndex = thisIndex;

                var cellNodes = tabEl.getElementsByTagName("cell");

                // Grow once to the highest index
                int maxIndex = -1;
                for (int j = 0; j < cellNodes.getLength(); j++) {
                    Element cellEl = (Element) cellNodes.item(j);
                    int idx = Integer.parseInt(cellEl.getAttribute("index"));
                    if (idx > maxIndex) maxIndex = idx;
                }
                if (maxIndex >= 0) {
                    panel.ensureCellIndex(maxIndex);
                }

                // Place apps
                int lastIndexAssigned = -1;
                for (int j = 0; j < cellNodes.getLength(); j++) {
                    Element cellEl = (Element) cellNodes.item(j);
                    int index = Integer.parseInt(cellEl.getAttribute("index"));
                    if (index != (lastIndexAssigned+1))
                    {
                    	index = lastIndexAssigned+1;
                    }
                    String path  = cellEl.getAttribute("path");

                    File bundle = new File(path);
                    if (bundle.exists() && index < panel.getComponentCount()) {
                        AppComponent app = MacAppUtils.createAppComponent(bundle);
                        if (app != null) {
                            ((LaunchCell) panel.getComponent(index)).setApp(app);
                            lastIndexAssigned++;
                        }
                    }
                }
            }

            // Restore selected tab (fallback to first tab if missing/invalid)
            if (tabs.getTabCount() > 0) {
                tabs.setSelectedIndex(selectedIndex >= 0 && selectedIndex < tabs.getTabCount() ? selectedIndex : 0);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
