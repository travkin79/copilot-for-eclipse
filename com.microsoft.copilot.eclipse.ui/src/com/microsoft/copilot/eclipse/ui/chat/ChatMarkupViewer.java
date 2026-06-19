// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.MultipleHyperlinkPresenter;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.mylyn.wikitext.markdown.MarkdownLanguage;
import org.eclipse.mylyn.wikitext.parser.css.CssParser;
import org.eclipse.mylyn.wikitext.ui.viewer.MarkupViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;

import com.microsoft.copilot.eclipse.core.CopilotCore;
import com.microsoft.copilot.eclipse.ui.CopilotUi;
import com.microsoft.copilot.eclipse.ui.utils.UiUtils;

class ChatMarkupViewer extends MarkupViewer {

  private static final String HEADER = """
      <?xml version='1.0' encoding='utf-8' ?>
      <html xmlns="http://www.w3.org/1999/xhtml">
      <head>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
      </head>
      <body>
      """;
  private static final String FOOTER = "</body>\n</html>";

  private static  final List<Extension> markdownExtensions = List.of(TablesExtension.create());
  private static final Parser markdownParser = Parser.builder()
          .extensions(markdownExtensions)
          .build();
  private static final HtmlRenderer renderer = HtmlRenderer.builder()
          .extensions(markdownExtensions)
          .build();

  public ChatMarkupViewer(Composite parent, int styles) {
    super(parent, null, styles);
    this.setMarkupLanguage(new MarkdownLanguage());
    this.setDisplayImages(false);

    IHyperlinkDetector[] hyperlinkDetectors = { new FileAnnotationHyperlinkDetector() };
    this.setHyperlinkDetectors(hyperlinkDetectors, SWT.NONE);

    MultipleHyperlinkPresenter hyperlinkPresenter = new MultipleHyperlinkPresenter((RGB) null);
    this.setHyperlinkPresenter(hyperlinkPresenter);

    // Register for chat font updates via centralized service
    var chatServiceManager = CopilotUi.getPlugin().getChatServiceManager();
    if (chatServiceManager != null) {
      chatServiceManager.getChatFontService().registerControl(getTextWidget());
    }
    loadStylesheet();
  }

  private void loadStylesheet() {
    if (UiUtils.isDarkTheme()) {
      URL cssUrl = CopilotUi.getPlugin().getBundle().getEntry("css/markup-viewer-dark.css");
      if (cssUrl != null) {
        try (Reader reader = new InputStreamReader(cssUrl.openStream(), StandardCharsets.UTF_8)) {
          this.setStylesheet(new CssParser().parse(reader));
        } catch (IOException e) {
          CopilotCore.LOGGER.error("Failed to load dark mode stylesheet for markup viewer", e);
        }
      }
    }
  }

  // MarkupViewer will write errors when failed to parse the markup, which will send the error to the Copilot.
  // so overwrite the setMarkup method to avoid sending the error.
  @Override
  public void setMarkup(String source) {
    try {
      String htmlText = this.computeHtml(source);
      setHtml(htmlText);
      // reset text presentation to update the style, otherwise the style won't be updated
      this.setTextPresentation(getTextPresentation());
    } catch (Throwable t) {
      if (getTextPresentation() != null) {
        getTextPresentation().clear();
      }
      setDocumentNoMarkup(new Document(source), new AnnotationModel());
      // TODO: Whether we should track the parse exception?
    }
  }

  // computeHtml(String) is a private method in MarkupViewer and we have to change its behavior, so we write our own
  private String computeHtml(String markupContent) {
    String escapedMarkup = markupContent
        .replace("<", "&lt;")
        .replace(">", "&gt;");
    String htmlCode = renderer.render(markdownParser.parse(escapedMarkup));
    return HEADER + htmlCode + FOOTER;
  }
}
