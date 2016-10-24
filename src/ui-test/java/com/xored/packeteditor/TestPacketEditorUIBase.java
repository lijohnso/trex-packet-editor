package com.xored.packeteditor;

import com.google.inject.Guice;
import com.xored.javafx.packeteditor.TRexPacketCraftingTool;
import com.xored.javafx.packeteditor.controllers.FieldEditorController;
import com.xored.javafx.packeteditor.guice.GuiceModule;
import com.xored.javafx.packeteditor.scapy.ScapyServerClient;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.Assert;
import org.junit.Before;
import org.slf4j.LoggerFactory;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.service.query.NodeQuery;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

import static javafx.scene.input.KeyCode.ENTER;
import static org.junit.Assert.fail;
import static org.testfx.api.FxAssert.verifyThat;

/**
 * Base class for UI tests. supports headless testing
 */
public class TestPacketEditorUIBase extends ApplicationTest {
    static org.slf4j.Logger logger = LoggerFactory.getLogger(TRexPacketCraftingTool.class);

    static boolean isHeadless() { return Boolean.getBoolean("headless"); }

    static {
        if (isHeadless()) {
            logger.info("using headless ui tests");
            System.setProperty("testfx.robot", "glass");
            System.setProperty("testfx.headless", "true");
            System.setProperty("prism.order", "sw");
            System.setProperty("prism.text", "t2k");
        }
    }

    @Before
    public void test_init() {
        interrupt(); // wait for JavaFX events to be handled
    }

    /** runs an action with the field */
    public <T extends Node> void with(String query, final Consumer<T> action) {
        action.accept(lookup(query).query());
    }

    /** puts a value to a editable combo box */
    public void setComboBoxText(String query, String val) {
        with(query, (ComboBox c)->c.getEditor().setText(val));
    }

    /** label contains a user model value */
    public boolean fieldLabelIsSet(Label label) {
        return label.getStyleClass().contains("field-value-set");
    }

    /** label contains a default value */
    public boolean fieldLabelIsDefault(Label label) {
        return label.getStyleClass().contains("field-value-default");
    }

    public void setFieldText(String query, String val) {
        clickOn(query); // click on label
        with(query, (Node node)->{
            if (node instanceof ComboBox) {
                ((ComboBox)node).getEditor().setText(val);
            } else {
                ((TextField)node).setText(val);
            }
            clickOn(node);
            push(ENTER);
        });
    }

    public void verifyUserModelFieldSet(String query) {
        verifyThat(query, this::fieldLabelIsSet);
    }

    public void verifyUserModelFieldDefault(String query) {
        verifyThat(query, this::fieldLabelIsDefault);
    }

    public void verifyUserModelFieldUnset(String query) {
        verifyThat(query, (Label l) -> !fieldLabelIsSet(l));
    }

    /**
     * Runs the specified {@link Runnable} on the
     * JavaFX application thread and waits for completion.
     *
     * @param action the {@link Runnable} to run
     * @throws NullPointerException if {@code action} is {@code null}
     */
    public static void runAndWait(Runnable action) throws InterruptedException, ExecutionException {
        if (action == null)
            throw new NullPointerException("action");

        // run synchronously on JavaFX thread
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        // queue on JavaFX thread and wait for completion
        final CountDownLatch doneLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                doneLatch.countDown();
            }
        });

        doneLatch.await();


        // TODO: javafx is still working with events, so some fields can't be accessed
        // this is dumb waiting for javafx fills the  fields
        Thread.sleep(2000);
    }

    public void runAndWait2(Runnable action) throws InterruptedException, ExecutionException {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get();

        // TODO: javafx is still working with events, so some fields can't be accessed
        // this is dumb waiting for javafx fills the  fields
        sleep(2000);
    }

    TRexPacketCraftingTool trex = new TRexPacketCraftingTool(Guice.createInjector(new GuiceModule()));
    FieldEditorController editorController = trex.getInjector().getInstance(FieldEditorController.class);
    URL resources;
    {
        try {
            resources = new URL(getClass().getResource("/"), "../../resources/test/");
        } catch (MalformedURLException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void start(Stage stage) {
        try {
            ScapyServerClient scapy;
            scapy = trex.getInjector().getInstance(ScapyServerClient.class);
            scapy.connect();
            trex.start(stage);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public void printNodesId() {
        NodeQuery query2 = lookup((Node t) -> {
            if (t.getId()!=null) {
                logger.debug("'" + t.getId() + "'");
                return true;
            }
            return false;
        });
    }

    void loadPcapFile(String filename) {
        loadPcapFileEx(filename, false);
    }

    void loadPcapFileEx(String filename, boolean mustfail) {
        final String[] error = {null};
        try {
            Assert.assertNotNull(editorController);
            runAndWait(() -> {
                try {
                    File file;
                    if (filename.startsWith("/")) {
                        file = new File(filename);
                    }
                    else {
                        file = new File(resources.getFile() + filename);
                    }
                    editorController.loadPcapFile(file);
                } catch (Exception e) {
                    error[0] = e.getMessage();
                }
            });
        } catch (InterruptedException e) {
            error[0] = e.getMessage();
        } catch (ExecutionException e) {
            error[0] = e.getMessage();
        }
        if (mustfail && error[0] == null) {
            fail("The test must fail, but it passed instead");
        }
        if (!mustfail && error[0] != null) {
            fail(error[0]);
        }
    }

    void savePcapFile(String filename) {
        savePcapFileEx(filename, false);
    }

    void savePcapFileEx(String filename, boolean mustfail) {
        final String[] error = {null};
        try {
            Assert.assertNotNull(editorController);
            runAndWait(() -> {
                try {
                    File file;
                    if (filename.startsWith("/")) {
                        file = new File(filename);
                    }
                    else {
                        file = new File(resources.getFile() + filename);
                    }
                    editorController.writeToPcapFile(file, true);
                } catch (Exception e) {
                    error[0] = e.getMessage();
                }
            });
        } catch (InterruptedException e) {
            error[0] = e.getMessage();
        } catch (ExecutionException e) {
            error[0] = e.getMessage();
        }
        if (mustfail && error[0] == null) {
            fail("The test must fail, but it passed instead");
        }
        if (!mustfail && error[0] != null) {
            fail(error[0]);
        }
    }

    void addLayer(String layerType) {
        //clickOn("Action");
        //clickOn("Add Protocol");
        clickOn(".protocol-type-selector .arrow-button");
        clickOn(layerType);
        clickOn("#append-protocol-button");
    }

    void selectProtoType(String proto) {
        clickOn("#Ether-type");
        clickOn("#Ether-type .arrow-button");
        //push(CONTROL, DOWN);
        clickOn(proto);
    }

    void recalculateAutoValues() {
        clickOn("Action");
        clickOn("Recalculate auto-values");
    }

    void newDocument() {
        clickOn("File");
        clickOn("New Document");
        //push(SHORTCUT, KeyCode.N);
    }

    void undo() {
        clickOn("Edit");
        clickOn("Undo");
        //push(SHORTCUT, KeyCode.Z);
    }

    void redo() {
        clickOn("Edit");
        clickOn("Redo");
        //push(SHORTCUT, KeyCode.R);
    }

    void scrollFieldsDown() {
        with("#fieldEditorScrollPane", (ScrollPane sp) -> sp.setVvalue(sp.getVmax()) );
    }

}
