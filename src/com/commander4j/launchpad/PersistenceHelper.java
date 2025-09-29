package com.commander4j.launchpad;

/*******************************************************************************
 * Title:        Commander4j
 * Description:  XML Persistence for LaunchPad (supports per-tab JScrollPane wrapper)
 * Author:       Dave (with ChatGPT assistance)
 * License:      GNU General Public License
 *******************************************************************************/

import java.io.File;
import java.io.FileOutputStream;

import javax.swing.JTabbedPane;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class PersistenceHelper
{
    private static final String CONFIG_PATH = "./xml/config/launchpad.xml";

    public static void saveState(JTabbedPane tabs)
    {
        try
        {
            var db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.newDocument();

            Element root = doc.createElement("launchpad");
            doc.appendChild(root);

            for (int t = 0; t < tabs.getTabCount(); t++)
            {
                Element tabEl = doc.createElement("tab");
                tabEl.setAttribute("name", tabs.getTitleAt(t));
                root.appendChild(tabEl);

                LaunchTabPanel tabPanel = unwrapPanel(tabs.getComponentAt(t));
                if (tabPanel == null) continue;

                for (int c = 0; c < tabPanel.getComponentCount(); c++)
                {
                    if (tabPanel.getComponent(c) instanceof LaunchCell cell)
                    {
                        AppComponent app = cell.getApp();
                        if (app != null)
                        {
                            Element cellEl = doc.createElement("cell");
                            cellEl.setAttribute("index", String.valueOf(c));
                            cellEl.setAttribute("path", app.getAppPath());

                            String customIcon = app.getCustomIconPath();
                            if (customIcon != null && !customIcon.isBlank())
                            {
                                cellEl.setAttribute("icon", customIcon);
                            }

                            tabEl.appendChild(cellEl);
                        }
                    }
                }
            }

            File outFile = new File(CONFIG_PATH);
            outFile.getParentFile().mkdirs();

            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.transform(new DOMSource(doc), new StreamResult(new FileOutputStream(outFile)));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void loadState(JTabbedPane tabs)
    {
        try {
            File inFile = new File(CONFIG_PATH);
            if (!inFile.exists()) return;

            var db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            var doc = db.parse(inFile);

            tabs.removeAll();

            var tabNodes = doc.getElementsByTagName("tab");
            for (int i = 0; i < tabNodes.getLength(); i++) {
                Element tabEl = (Element) tabNodes.item(i);
                String name = tabEl.getAttribute("name");

                LaunchTabPanel panel = new LaunchTabPanel();
                // wrap each tab in a vertical-only scroller
                JScrollPane sp = new JScrollPane(
                    panel,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                );
                sp.setBorder(null);
                tabs.addTab(name, sp);

                var cellNodes = tabEl.getElementsByTagName("cell");

                // grow once to fit highest index
                int maxIndex = -1;
                for (int j = 0; j < cellNodes.getLength(); j++) {
                    Element cellEl = (Element) cellNodes.item(j);
                    int idx = Integer.parseInt(cellEl.getAttribute("index"));
                    if (idx > maxIndex) maxIndex = idx;
                }
                if (maxIndex >= 0) {
                    panel.ensureCellIndex(maxIndex);
                }

                for (int j = 0; j < cellNodes.getLength(); j++) {
                    Element cellEl = (Element) cellNodes.item(j);
                    int index = Integer.parseInt(cellEl.getAttribute("index"));
                    String path  = cellEl.getAttribute("path");

                    File bundle = new File(path);
                    if (bundle.exists() && index < panel.getComponentCount()) {
                        AppComponent app = MacAppUtils.createAppComponent(bundle);
                        if (app != null) {
                            ((LaunchCell) panel.getComponent(index)).setApp(app);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Helper: unwrap a LaunchTabPanel from a tab component that might be a JScrollPane. */
    private static LaunchTabPanel unwrapPanel(java.awt.Component c) {
        if (c instanceof LaunchTabPanel p) return p;
        if (c instanceof JScrollPane sp) {
            var v = (sp.getViewport() != null) ? sp.getViewport().getView() : null;
            if (v instanceof LaunchTabPanel p) return p;
        }
        return null;
    }
}
