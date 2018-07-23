package com.sandec.mdfx;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.attributes.AttributeNode;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.attributes.AttributesNode;
import com.vladsch.flexmark.ext.attributes.internal.AttributesNodePostProcessor;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Pair;
import com.vladsch.flexmark.Extension;
import com.vladsch.flexmark.ext.tables.*;
import com.vladsch.flexmark.parser.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

class MDFXNodeHelper extends VBox {
  String mdString;

  MDFXNode parent;

  final static String ITALICE_CLASS_NAME = "markdown-italic";
  final static String BOLD_CLASS_NAME = "markdown-bold";

  List<String> elemStyleClass = new LinkedList<String>();

  List<Consumer<Pair<Node,String>>> elemFunctions = new LinkedList<Consumer<Pair<Node,String>>>();

  Boolean nodePerWord = false;

  List<String> styles = new LinkedList<String>();

  VBox root = new VBox();

  GridPane grid = null;
  int gridx = 0;
  int gridy = 0;
  TextFlow flow = null;

  int[] currentChapter = new int[6];

  public boolean shouldShowContent() {
    return parent.showChapter(currentChapter);
  }

  public void newParagraph() {
    TextFlow newFlow = new TextFlow();
    newFlow.getStyleClass().add("markdown-normal-flow");
    root.getChildren().add(newFlow);
    flow = newFlow;
  }

  public MDFXNodeHelper(MDFXNode parent, String mdstring) {
    this.parent = parent;

    getStylesheets().add("/com/sandec/mdfx/mdfx.css");

    root.getStyleClass().add("markdown-paragraph-list");
    root.setFillWidth(true);

    LinkedList<Extension> extensions = new LinkedList();
    extensions.add(TablesExtension.create());
    extensions.add(AttributesExtension.create());
    Parser parser = Parser.builder().extensions(extensions).build();

    com.vladsch.flexmark.ast.Document node = parser.parse(mdstring);

    new MDParser(node).visitor.visitChildren(node);

    this.getChildren().add(root);
  }


  class MDParser {

    Document document;

    MDParser(Document document) {
      this.document = document;
    }

    NodeVisitor visitor = new NodeVisitor(
            //new VisitHandler<>(com.vladsch.flexmark.ast.Node.class, this::visit),
            new VisitHandler<>(Code.class, this::visit),
            new VisitHandler<>(CustomBlock.class, this::visit),
            new VisitHandler<>(Document.class, this::visit),
            new VisitHandler<>(Emphasis.class, this::visit),
            new VisitHandler<>(StrongEmphasis.class, this::visit),
            new VisitHandler<>(FencedCodeBlock.class, this::visit),
            new VisitHandler<>(SoftLineBreak.class, this::visit),
            new VisitHandler<>(HardLineBreak.class, this::visit),
            new VisitHandler<>(Heading.class, this::visit),
            new VisitHandler<>(ListItem.class, this::visit),
            new VisitHandler<>(BulletListItem.class, this::visit),
            new VisitHandler<>(OrderedListItem.class, this::visit),
            new VisitHandler<>(BulletList.class, this::visit),
            new VisitHandler<>(OrderedList.class, this::visit),
            new VisitHandler<>(Paragraph.class, this::visit),
            new VisitHandler<>(com.vladsch.flexmark.ast.Image.class, this::visit),
            new VisitHandler<>(Link.class, this::visit),
            new VisitHandler<>(com.vladsch.flexmark.ast.TextBase.class, this::visit),
            new VisitHandler<>(com.vladsch.flexmark.ast.Text.class, this::visit),
            new VisitHandler<>(TableHead.class, this::visit),
            new VisitHandler<>(TableBody.class, this::visit),
            new VisitHandler<>(TableRow.class, this::visit),
            new VisitHandler<>(TableCell.class, this::visit)
    );

    public void visit(Code code) {

      Label label = new Label(code.getText().normalizeEndWithEOL());
      label.getStyleClass().add("markdown-code");

      Region bgr1 = new Region();
      bgr1.setManaged(false);
      bgr1.getStyleClass().add("markdown-code-background");
      label.boundsInParentProperty().addListener((p, oldV, newV) -> {
        bgr1.setTranslateX(newV.getMinX() + 2);
        bgr1.setTranslateY(newV.getMinY() - 2);
        bgr1.resize(newV.getWidth() - 4, newV.getHeight() + 4);
      });

      flow.getChildren().add(bgr1);
      flow.getChildren().add(label);

      visitor.visitChildren(code);
    }

    public void visit(CustomBlock customBlock) {
      flow.getChildren().add(new Text("\n\n"));
      visitor.visitChildren(customBlock);
    }


    public void visit(Document document) {
      visitor.visitChildren(document);
    }

    public void visit(Emphasis emphasis) {
      elemStyleClass.add(ITALICE_CLASS_NAME);
      visitor.visitChildren(emphasis);
      elemStyleClass.remove(ITALICE_CLASS_NAME);
    }

    public void visit(StrongEmphasis strongEmphasis) {
      elemStyleClass.add(BOLD_CLASS_NAME);
      visitor.visitChildren(strongEmphasis);
      elemStyleClass.remove(BOLD_CLASS_NAME);
    }

    public void visit(FencedCodeBlock fencedCodeBlock) {

      if(!shouldShowContent()) return;

      Label label = new Label(fencedCodeBlock.getChars().toString());
      label.getStyleClass().add("markdown-codeblock");
      VBox vbox = new VBox(label);
      vbox.getStyleClass().add("markdown-codeblock-box");

      root.getChildren().add(vbox);
      //flow.styleClass ::= "markdown-normal-flow"
      //flow <++ new Text("\n")
      visitor.visitChildren(fencedCodeBlock);
    }

    public void visit(SoftLineBreak softLineBreak) {
      //flow <++ new Text("\n")
      addText(" ", "");
      visitor.visitChildren(softLineBreak);
    }

    public void visit(HardLineBreak hardLineBreak) {
      flow.getChildren().add(new Text("\n"));
      visitor.visitChildren(hardLineBreak);
    }


    public void visit(Heading heading) {

      if (heading.getLevel() == 1 || heading.getLevel() == 2) {
        currentChapter[heading.getLevel()] += 1;

        IntStream.rangeClosed(heading.getLevel() + 1, currentChapter.length - 1).forEach(i -> {
          currentChapter[i] = 0;
        });
      }

      if (shouldShowContent()) {
        newParagraph();

        flow.getStyleClass().add("markdown-heading-" + heading.getLevel());
        flow.getStyleClass().add("markdown-heading");

        visitor.visitChildren(heading);
      }
    }


    public void visit(ListItem listItem) {

      if(!shouldShowContent()) return;

      // add new listItem
      VBox oldRoot = root;

      VBox newRoot = new VBox();
      newRoot.getStyleClass().add("markdown-vbox1");
      newRoot.getStyleClass().add("markdown-paragraph-list");
      newRoot.setFillWidth(true);

      Label label = new Label(" • ");
      label.setMinWidth(20);

      HBox hbox = new HBox();
      hbox.getStyleClass().add("markdown-hbox1");
      hbox.getChildren().add(label);
      hbox.setAlignment(Pos.TOP_LEFT);
      hbox.getChildren().add(newRoot);


      oldRoot.getChildren().add(hbox);

      root = newRoot;

      visitor.visitChildren(listItem);
      root = oldRoot;
    }

    public void visit(BulletList bulletList) {

      if(!shouldShowContent()) return;

      VBox oldRoot = root;
      root = new VBox();
      oldRoot.getChildren().add(root);
      newParagraph();
      flow.getStyleClass().add("markdown-normal-flow");
      visitor.visitChildren(bulletList);
      root = oldRoot;
    }

    public void visit(OrderedList orderedList) {
      VBox oldRoot = root;
      root = new VBox();
      oldRoot.getChildren().add(root);
      newParagraph();
      flow.getStyleClass().add("markdown-normal-flow");
      visitor.visitChildren(orderedList);
      root = oldRoot;
    }

    public void visit(Paragraph paragraph) {
        if(!shouldShowContent()) return;

      List<AttributesNode> atts = AttributesExtension.NODE_ATTRIBUTES.getFrom(document).get(paragraph);
      newParagraph();
      flow.getStyleClass().add("markdown-normal-flow");
      setAttrs(atts,true);
      visitor.visitChildren(paragraph);
      setAttrs(atts,false);
    }

    public void visit(com.vladsch.flexmark.ast.Image image) {
      // TODO  flow.getChildren().add(new ImageView(new Image(image.getUrlContent().toString())));
      visitor.visitChildren(image);
    }

    public void visit(Link link) {

      LinkedList<Node> nodes = new LinkedList<>();

      Consumer<Pair<Node, String>> addProp = (pair) -> {
        Node node = pair.getKey();
        String txt = pair.getValue();
        nodes.add(node);

        node.getStyleClass().add("markdown-link");
        parent.setLink(node, link.getUrl().normalizeEndWithEOL(), txt);
      };
      Platform.runLater(() -> {
        BooleanProperty lastValue = new SimpleBooleanProperty(false);
        Runnable updateState = () -> {
          boolean isHover = nodes.stream().map(node -> node.isHover()).collect(Collectors.toList()).contains(true);
          if (isHover != lastValue.get()) {
            lastValue.set(isHover);
            nodes.stream().forEach(node -> {
              if (isHover) {
                node.getStyleClass().add("markdown-link-hover");
              } else {
                node.getStyleClass().remove("markdown-link-hover");
              }

            });
          }

        };

        nodes.stream().forEach(node -> {
          node.hoverProperty().addListener((p, o, n) -> updateState.run());
        });
        updateState.run();
      });

      boolean oldNodePerWord = nodePerWord;
      nodePerWord = true;
      elemFunctions.add(addProp);
      visitor.visitChildren(link);
      nodePerWord = oldNodePerWord;
      elemFunctions.remove(addProp);
    }

    public void visit(com.vladsch.flexmark.ast.TextBase text) {
      List<AttributesNode> atts = AttributesExtension.NODE_ATTRIBUTES.getFrom(document).get(text);
      setAttrs(atts,true);
      visitor.visitChildren(text);
      setAttrs(atts,false);
    }

    public void visit(com.vladsch.flexmark.ast.Text text) {
      visitor.visitChildren(text);

      String wholeText = text.getChars().normalizeEOL();

      String[] textsSplitted = null;
      if (nodePerWord) {
        textsSplitted = text.getChars().normalizeEOL().split(" ");
      } else {
        textsSplitted = new String[1];
        textsSplitted[0] = text.getChars().normalizeEOL();
      }
      final String[] textsSplittedFinal = textsSplitted;

      IntStream.rangeClosed(0, textsSplitted.length - 1).forEach(i -> {
        if (i == 0) {
          addText(textsSplittedFinal[i], wholeText);
        } else {
          addText(" " + textsSplittedFinal[i], wholeText);
        }
      });
    }

    public void visit(TableHead customNode) {
      TextFlow oldFlow = flow;
      grid = new GridPane();
      grid.getStyleClass().add("markdown-table-table");
      gridx = 0;
      gridy = -1;
      root.getChildren().add(grid);

      visitor.visitChildren(customNode);


      IntStream.rangeClosed(1, gridx).forEach(i -> {
        ColumnConstraints constraint = new ColumnConstraints();
        if (i == gridx) {
          constraint.setPercentWidth(100.0 * (2.0 / (gridx + 1.0)));
        }
        grid.getColumnConstraints().add(constraint);
      });

      flow = oldFlow;
      newParagraph();
      //flow.styleClass ::= "markdown-normal-flow"
    }

    public void visit(TableBody customNode) {
      visitor.visitChildren(customNode);
      //} else if(customNode instanceof TableBlock) {
      //  super.visit(customNode);
    }

    public void visit(TableRow customNode) {
      if(customNode.getRowNumber() != 0) {
        gridx = 0;
        gridy += 1;
        visitor.visitChildren(customNode);
      }
    }

    public void visit(TableCell customNode) {
      TextFlow oldFlow = flow;
      flow = new TextFlow();
      flow.getStyleClass().add("markdown-normal-flow");
      TextFlow container = flow;
      //  println("grid: " + grid)
      //  javafx.scene.layout.GridPane.setHgrow(container,Priority.ALWAYS)
      flow.setPrefWidth(9999);
      flow.getStyleClass().add("markdown-table-cell");
      if (gridy == 0) {
        flow.getStyleClass().add("markdown-table-cell-top");
      }
      if (gridy % 2 == 0) {
        flow.getStyleClass().add("markdown-table-odd");
      } else {
        flow.getStyleClass().add("markdown-table-even");
      }
      grid.add(container, gridx, gridy);
      gridx += 1;
      visitor.visitChildren(customNode);
    }

    public void setAttrs(List<AttributesNode> atts, boolean add) {
      if(atts == null) return;

      List<com.vladsch.flexmark.ast.Node> atts2 = atts.stream().flatMap(x -> StreamSupport.stream(x.getChildren().spliterator(),false)).collect(Collectors.toList());
      List<AttributeNode> atts3 = (List<AttributeNode>) (Object) atts2;

      atts3.forEach(att -> {
        if(att.getName().toLowerCase().equals("style")) {
          if(add) styles.add(att.getValue().toString());
          else styles.remove(att.getValue().toString());
        }
        if(att.isClass()) {
          if(add) elemStyleClass.add(att.getValue().toString());
          else elemStyleClass.remove(att.getValue().toString());
        }
      });
    }

  }

  public void addText(String text, String wholeText) {
    if(!text.isEmpty()) {

      Text toAdd = new Text(text);

      toAdd.getStyleClass().add("markdown-text");
      elemStyleClass.stream().forEach(elemStyleClass -> {
        toAdd.getStyleClass().add(elemStyleClass);
      });
      elemFunctions.stream().forEach(f -> {
        f.accept(new Pair(toAdd,wholeText));
      });
      if(!styles.isEmpty()) {
        toAdd.setStyle(styles.stream().collect(Collectors.joining(";")));
      }

      flow.getChildren().add(toAdd);
    }
  }
}
