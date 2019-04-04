/*
 *  Copyright (C) 2018 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jloda.fx.control;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * a splittable tab pane
 * Daniel Huson, 4.2019
 */
public class SplittableTabPane extends Pane {

    private final ObjectProperty<TabPane> focusedTabPane = new SimpleObjectProperty<>();
    private final ASingleSelectionModel<Tab> selectionModel = new ASingleSelectionModel<>();

    private ObservableList<Tab> tabs = FXCollections.observableArrayList();

    private ObservableMap<TabPane, SplitPane> tabPane2ParentSplitPane = FXCollections.observableHashMap();

    private static final String TAB_DRAG_KEY = "tab";
    private final ObjectProperty<Tab> draggingTab = new SimpleObjectProperty<>();

    /**
     * constructor
     */
    public SplittableTabPane() {
        final TabPane tabPane = createTabPane();
        tabPane.prefWidthProperty().bind(widthProperty());
        tabPane.prefHeightProperty().bind(heightProperty());

        final SplitPane splitPane = createSplitPane(tabPane);
        tabPane2ParentSplitPane.put(tabPane, splitPane);

        splitPane.setOrientation(Orientation.HORIZONTAL);
        getChildren().add(splitPane);

        tabs.addListener((ListChangeListener<Tab>) (c) -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Tab tab : c.getAddedSubList()) {
                        final TabPane target;
                        if (getFocusedTabPane() != null)
                            target = getFocusedTabPane();
                        else {
                            target = findATabPane(splitPane.getItems());
                        }
                        moveTab(tab, null, target);
                        setupDrag(tab);
                    }
                } else if (c.wasRemoved()) {
                    for (Tab tab : c.getRemoved()) {
                        moveTab(tab, tab.getTabPane(), null);
                    }
                }
            }
            selectionModel.setItems(tabs);
        });

        selectionModel.selectedItemProperty().addListener((c, o, n) -> {
            setFocusedTabPane(n != null ? n.getTabPane() : null);
            System.err.println("Selected: " + n);
        });
    }

    /**
     * move tab to other tab pane and update context menu accordingly
     *
     * @param tab
     * @param oldTabPane if not null, removed from here
     * @param newTabPane if not null, added to here
     */
    private void moveTab(Tab tab, TabPane oldTabPane, TabPane newTabPane) {
        if (oldTabPane != null) {
            final SplitPane oldSplitPane = tabPane2ParentSplitPane.get(oldTabPane);
            oldTabPane.getTabs().remove(tab);
            if (oldTabPane.getTabs().size() == 0) {
                oldSplitPane.getItems().remove(oldTabPane);
                tabPane2ParentSplitPane.remove(oldTabPane);
            }
        }

        if (newTabPane != null) {
            newTabPane.getTabs().add(tab);
            setupMenu(tab, newTabPane, tabPane2ParentSplitPane.get(newTabPane));
            newTabPane.getSelectionModel().select(tab);
        } else {
            tabs.remove(tab);
        }
    }

    /**
     * split the given tab pane in the given orientation and move the tab into the new tab pane
     *
     * @param orientation
     * @param tab
     * @param tabPane
     */
    private void split(Orientation orientation, Tab tab, TabPane tabPane) {
        final SplitPane parentSplitPane = tabPane2ParentSplitPane.get(tabPane);

        if (parentSplitPane.getItems().size() == 1 && parentSplitPane.getOrientation() != orientation)
            parentSplitPane.setOrientation(orientation);

        if (parentSplitPane.getOrientation() == orientation) { // desired split has same orientation as parent split parent, add
            final TabPane newTabPane = createTabPane();
            final double[] dividers = addDivider(parentSplitPane.getDividerPositions());
            parentSplitPane.getItems().add(newTabPane);
            parentSplitPane.setDividerPositions(dividers);
            tabPane2ParentSplitPane.put(newTabPane, parentSplitPane);
            moveTab(tab, tabPane, newTabPane);
        } else { // change of orientation, create new split pane

            final TabPane newTabPane = createTabPane();
            final SplitPane splitPane = createSplitPane(tabPane, newTabPane);

            tabPane2ParentSplitPane.put(tabPane, splitPane);
            tabPane2ParentSplitPane.put(newTabPane, splitPane);

            final IntegerProperty splitPaneSize = new SimpleIntegerProperty();
            splitPaneSize.bind(Bindings.size(splitPane.getItems()));

            splitPaneSize.addListener((c, o, n) -> {
                if (o.intValue() > 0 && n.intValue() == 0) {
                    parentSplitPane.getItems().remove(splitPane);
                } else if (o.intValue() > 1 && n.intValue() == 1) {
                    final TabPane lastTabPane = (TabPane) splitPane.getItems().get(0);
                    final int index = parentSplitPane.getItems().indexOf(splitPane);
                    if (index != -1) {
                        parentSplitPane.getItems().set(index, lastTabPane);
                    } else {
                        final double[] dividers = addDivider(parentSplitPane.getDividerPositions());
                        parentSplitPane.getItems().add(lastTabPane);
                        parentSplitPane.setDividerPositions(dividers);
                    }
                    parentSplitPane.getItems().remove(splitPane);
                    tabPane2ParentSplitPane.put(lastTabPane, parentSplitPane);
                }
            });

            splitPane.setOrientation(orientation);
            moveToOpposite(tab, tabPane);

            final int index = parentSplitPane.getItems().indexOf(tabPane);
            final double[] dividers = parentSplitPane.getDividerPositions();
            parentSplitPane.getItems().remove(tabPane);
            parentSplitPane.getItems().add(index, splitPane);
            parentSplitPane.setDividerPositions(dividers);
        }
    }

    /**
     * add a new divider
     */
    private static double[] addDivider(double[] oldDividers) {
        if (oldDividers.length == 0)
            return new double[]{0.5};
        else {
            final double[] dividers = new double[oldDividers.length + 1];
            System.arraycopy(oldDividers, 0, dividers, 0, oldDividers.length);
            dividers[oldDividers.length] = 0.5 * (1 + oldDividers[oldDividers.length - 1]);
            return dividers;
        }
    }

    /**
     * move a tab to the opposite tab pane
     *
     * @param tab     to be moved
     * @param tabPane the pane from which the tab will be moved
     */
    private void moveToOpposite(Tab tab, TabPane tabPane) {
        final SplitPane parentSplitPane = tabPane2ParentSplitPane.get(tabPane);
        if (parentSplitPane.getItems().size() == 1) { // there is no opposite, need to split
            split(parentSplitPane.getOrientation(), tab, tabPane);
        } else {
            final int index = parentSplitPane.getItems().indexOf(tabPane);
            if (index != -1) {
                // try to find another tab in this split pane:
                for (int i = parentSplitPane.getItems().size() - 1; i >= 0; i--) {
                    if (i != index && parentSplitPane.getItems().get(i) instanceof TabPane) {
                        final TabPane target = (TabPane) (parentSplitPane.getItems().get(i));
                        if (target != null) {
                            moveTab(tab, tabPane, target);
                            return;
                        }
                    }
                }
                // find a tab in other contained split pane:
                for (int i = parentSplitPane.getItems().size() - 1; i >= 0; i--) {
                    if (i != index) {
                        final TabPane target = findATabPane(Collections.singleton(parentSplitPane.getItems().get(i)));
                        if (target != null) {
                            moveTab(tab, tabPane, target);
                            return;
                        }
                    }
                }
            }
        }
    }

    private TabPane findATabPane(Collection<Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof TabPane)
                return (TabPane) node;
            else if (node instanceof SplitPane) {
                final TabPane result = findATabPane(((SplitPane) node).getItems());
                if (result != null)
                    return result;
            }
        }
        return null;
    }

    /**
     * setup the menu when inserting a tab
     *
     * @param tab
     * @param tabPane
     * @param splitPane
     */
    private void setupMenu(Tab tab, TabPane tabPane, SplitPane splitPane) {
        final ArrayList<MenuItem> menuItems = new ArrayList<>();

        if (tab.isClosable()) {
            final MenuItem close = new MenuItem("Close");
            close.setOnAction((e) -> tabs.remove(tab));
            close.disableProperty().bind(Bindings.size(tabs).isEqualTo(0).or(tab.closableProperty().not()));
            menuItems.add(close);
            final MenuItem closeOthers = new MenuItem("Close Others");
            closeOthers.setOnAction((e) -> {
                final ArrayList<Tab> toClose = new ArrayList<>(tabPane.getTabs());
                toClose.remove(tab);
                tabs.removeAll(toClose);
            });
            closeOthers.disableProperty().bind(Bindings.size(tabPane.getTabs()).lessThan(2).or(tab.closableProperty().not()));
            menuItems.add(closeOthers);
            final MenuItem closeAll = new MenuItem("Close All");
            closeAll.setOnAction((e) -> tabs.removeAll(tabPane.getTabs()));
            closeAll.disableProperty().bind(tab.closableProperty().not());
            menuItems.add(closeAll);

            tab.setClosable(true);
            tab.setOnCloseRequest((e) -> close.getOnAction().handle(null));
        }

        final MenuItem splitVertically = new MenuItem("Split Vertically");
        splitVertically.setOnAction((e) -> split(Orientation.HORIZONTAL, tab, tabPane));
        splitVertically.disableProperty().bind(Bindings.size(tabPane.getTabs()).lessThan(2));
        menuItems.add(splitVertically);

        final MenuItem splitHorizontally = new MenuItem("Split Horizontally");
        splitHorizontally.setOnAction((e) -> split(Orientation.VERTICAL, tab, tabPane));
        splitHorizontally.disableProperty().bind(Bindings.size(tabPane.getTabs()).lessThan(2));
        menuItems.add(splitHorizontally);

        menuItems.add(new SeparatorMenuItem());

        final MenuItem moveToOpposite = new MenuItem("Move to Opposite");
        moveToOpposite.setOnAction((e) -> moveToOpposite(tab, tab.getTabPane()));
        moveToOpposite.disableProperty().bind(Bindings.size(tabPane.getTabs()).lessThan(1));
        menuItems.add(moveToOpposite);

        menuItems.add(new SeparatorMenuItem());

        final MenuItem changeSplitOrientation = new MenuItem("Change Split Orientation");
        changeSplitOrientation.setOnAction((e) -> {
            splitPane.setOrientation(splitPane.getOrientation() == Orientation.VERTICAL ? Orientation.HORIZONTAL : Orientation.VERTICAL);
        });
        menuItems.add(changeSplitOrientation);

        final ArrayList<MenuItem> existingItems;
        if (tab.getContextMenu() != null) {
            existingItems = new ArrayList<>(tab.getContextMenu().getItems());
            int index = findMenuItem(changeSplitOrientation.getText(), existingItems);
            while (index >= 0)
                existingItems.remove(index--);
        } else
            existingItems = null;
        tab.setContextMenu(new ContextMenu(menuItems.toArray(new MenuItem[0])));

        if (existingItems != null && existingItems.size() > 0) {
            if (!(existingItems.get(0) instanceof SeparatorMenuItem)) {
                tab.getContextMenu().getItems().add(new SeparatorMenuItem());
            }
            tab.getContextMenu().getItems().addAll(existingItems);
        }
    }

    /**
     * find the first occurrence of a menu item with the given text
     *
     * @param text
     * @param menuItems
     * @return index or -1
     */
    private static int findMenuItem(String text, ArrayList<MenuItem> menuItems) {
        for (int i = 0; i < menuItems.size(); i++) {
            if (text.equals(menuItems.get(i).getText()))
                return i;
        }
        return -1;
    }

    public ASingleSelectionModel<Tab> getSelectionModel() {
        return selectionModel;
    }

    private SplitPane createSplitPane(TabPane... tabPanes) {
        final SplitPane splitPane = new SplitPane(tabPanes);
        splitPane.prefWidthProperty().bind(widthProperty());
        splitPane.prefHeightProperty().bind(heightProperty());

        final double[] positions = new double[tabPanes.length - 1];
        for (int i = 1; i < tabPanes.length; i++) {
            positions[i - 1] = i * (1.0 / tabPanes.length);
        }
        splitPane.setDividerPositions(positions);
        return splitPane;
    }

    /**
     * create a tab pane
     *
     * @return new tab pane
     */
    private TabPane createTabPane() {
        final TabPane tabPane = new TabPane();
        tabPane.getSelectionModel().selectedItemProperty().addListener((c, o, n) -> {
            if (n == null)
                selectionModel.clearSelection();
            else
                selectionModel.select(n);
        });
        tabPane.focusedProperty().addListener((c, o, n) -> {
            if (n) {
                if (tabPane.getSelectionModel().isEmpty())
                    selectionModel.clearSelection();
                else
                    selectionModel.select(tabPane.getSelectionModel().getSelectedItem());
            }
        });
        setupDrop(tabPane);
        return tabPane;
    }

    /**
     * get all the tabs. Add tabs to this
     *
     * @return tabs
     */
    public ObservableList<Tab> getTabs() {
        return tabs;
    }

    public TabPane getFocusedTabPane() {
        return focusedTabPane.get();
    }

    public ObjectProperty<TabPane> focusedTabPaneProperty() {
        return focusedTabPane;
    }

    public void setFocusedTabPane(TabPane focusedTabPane) {
        this.focusedTabPane.set(focusedTabPane);
    }

    private void setupDrag(Tab tab) {
        final Label label = new Label(tab.getText());
        label.setGraphic(tab.getGraphic());
        tab.setText("");
        tab.setGraphic(label);
        tab.textProperty().addListener((c, o, n) -> {
            if (n.length() > 0) {
                label.setText(n);
                Platform.runLater(() -> tab.setText(""));
            }
        });
        tab.graphicProperty().addListener((c, o, n) -> {
            if (n != label) {
                label.setGraphic(n);
                Platform.runLater(() -> tab.setGraphic(label));
            }
        });

        label.setOnDragDetected(event -> {
            Dragboard dragboard = label.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(TAB_DRAG_KEY);
            dragboard.setContent(clipboardContent);
            draggingTab.set(tab);
            event.consume();
        });
    }

    private void setupDrop(TabPane tabPane) {
        tabPane.setOnDragOver(event -> {
            final Dragboard dragboard = event.getDragboard();
            if (dragboard.hasString()
                    && TAB_DRAG_KEY.equals(dragboard.getString())
                    && draggingTab.get() != null
                    && draggingTab.get().getTabPane() != tabPane) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        tabPane.setOnDragDropped(event -> {
            final Dragboard dragboard = event.getDragboard();
            if (dragboard.hasString() && TAB_DRAG_KEY.equals(dragboard.getString()) && draggingTab.get() != null && draggingTab.get().getTabPane() != tabPane) {
                final Tab tab = draggingTab.get();
                moveTab(tab, tab.getTabPane(), tabPane);
                event.setDropCompleted(true);
                draggingTab.set(null);
                event.consume();
            }
        });
    }
}