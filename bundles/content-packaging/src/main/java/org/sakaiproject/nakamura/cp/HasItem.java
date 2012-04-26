/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.sakaiproject.nakamura.cp;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.lom.type.JSONUtil;

import java.util.ArrayList;
import java.util.List;

public class HasItem extends HasMetadata{

  private List<Item> items;
  private String title;
  public static enum ITEMTYPE {ORGANIZATION, ITEM};
  protected ITEMTYPE type;

  public HasItem() {
    super();
  }
  
  @Override
  protected void init() {
    super.init();
    String itemName = "item";
    String titleName = "title";
    JSONObject itemJSON = JSONUtil.getJSONObject(json, itemName);
    if (itemJSON == null){
      JSONArray itemArray = JSONUtil.getJSONArray(json, itemName);
      if (itemArray != null) {
        for (int i = 0; i < itemArray.length(); i++) {
          JSONObject object = itemArray.optJSONObject(i);
          if (object != null) {
            addItem(new Item(object));
          }
        }
      }
    } else {
      addItem(new Item(itemJSON));
    }
    title = JSONUtil.getStringValue(json, titleName);
    if (title == null) {
      title = "";
    }
  }
  
  public List<Item> getItems() {
    return items;
  }

  public void addItem(Item item, int index) {
    if (items == null) {
      items = new ArrayList<Item>();
    }
    items.add(index, item);
  }
  
  public void addItem(Item item) {
    if (items == null) {
      items = new ArrayList<Item>();
    }
    items.add(item);
  }
  
  public Item searchSubItem(String id) {
    if (items == null || id == null || "".equals(id)) {
      return null;
    }
    for (int i = 0; i < items.size(); i++) {
      if (id.equalsIgnoreCase(items.get(i).getIdentifier())){
        return items.get(i);
      }
    }
    for (Item item : items) {
      Item i = item.searchSubItem(id);
      if (i != null) {
        return i;
      }
    }
    return null;
  }
  
  public void setItems(List<Item> items) {
    this.items = items;
  }
  
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }
  
  public boolean hasSubItems() {
    if (items == null) {
      return false;
    }
    return true;
  }

  public ITEMTYPE getType() {
    return type;
  }
  @Override
  public String generateXML() {
    StringBuilder sb = new StringBuilder(super.generateXML());
    if (this.getTitle() != null) {
      sb.append("<title>" + this.getTitle() + "</title>");
    }
    if (this.hasSubItems()) {
      for (int i = 0; i < this.getItems().size(); i++) {
        sb.append(this.getItems().get(i).generateXML());
      }
    }
    if (sb.toString().equals("")) {
      return sb.toString();
    }
    return new String("" + sb.toString() + "");
  }
}
