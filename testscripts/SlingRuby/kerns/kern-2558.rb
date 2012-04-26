#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers

class TC_Kern2558 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @m = uniqueness
    @s.switch_user(User.admin_user)
    @world_service = @s.url_for("/system/world/create")
  end

  def test_create_group_with_custom_style
    template_path = "/var/templates/test/kern-2558/simple-test-template-#{@m}"
    create_template_with_style(true, template_path)
    group_id = "testgroupstyle#{@m}"

    # Create a group using a template with a customStyle set
    result = @s.execute_post(@world_service, {
        "data" => <<-eos
        {
          "id": "#{group_id}",
          "title" : "Group Title",
          "description" : "Group Description",
          "worldTemplate" : "#{template_path}",
          "visibility": "public",
          "joinability": "no"
        }
        eos
    })
    assert_equal(200, result.code.to_i)

    # grab the group data and verify the customStyle param has been set properly
    res = @s.execute_get(@s.url_for("/system/userManager/group/#{group_id}.json"))
    assert_equal(200, res.code.to_i)
    json = JSON.parse(res.body)
    assert_equal("/dev/skins/default/skin.css", json['properties']['sakai:customStyle'])
  end

  def test_create_group_with_no_style
    nostyle_template_path = "/var/templates/test/kern-2558/simple-test-template-nostyle-#{@m}"
    create_template_with_style(false, nostyle_template_path)
    group_id = "testgroupnostyle#{@m}"

    # Create a group using a template with no style set
    result = @s.execute_post(@world_service, {
        "data" => <<-eos
        {
          "id": "#{group_id}",
          "title" : "Group Title",
          "description" : "Group Description",
          "worldTemplate" : "#{nostyle_template_path}",
          "visibility": "public",
          "joinability": "no"
        }
        eos
    })
    assert_equal(200, result.code.to_i)

    # grab the group data and verify the customStyle param does not exist
    res = @s.execute_get(@s.url_for("/system/userManager/group/#{group_id}.json"))
    assert_equal(200, res.code.to_i)
    json = JSON.parse(res.body)
    assert_nil(json['properties']['sakai:customStyle'])
  end

  def create_template_with_style(include_style, path)

    template = <<-eos
    {
      "id": "simplegroupstyle",
      "title": "__MSG__SIMPLE_GROUP__",
      "img": "/dev/images/worldtemplates/simplegroup.png",
      "fullImg": "/dev/images/worldtemplates/simplegroup-full.png",
      "perfectFor": "__MSG__SIMPLE_GROUP_PERFECT_FOR__",
    eos
    if include_style
      template += '"customStyle": "/dev/skins/default/skin.css",'
    end
    template += <<-eos
      "roles": [
        {
          "id": "member",
          "title": "MEMBER",
          "titlePlural": "MEMBERS",
          "isManagerRole": false,
          "manages":[]
        },
        {
          "id": "manager",
          "title": "MANAGER",
          "titlePlural":"MANAGERS",
          "isManagerRole":true,
          "manages":[
            "member"
          ]
        }
      ],
      "joinRole": "member",
      "creatorRole": "manager",
      "docs": {
        "${pid}0": {
          "structure0": {
            "library":{
              "_ref":"${refid}0",
              "_order":0,
              "_nonEditable": true,
              "_title": "Library",
              "main":{
                "_ref":"${refid}0",
                "_order":0,
                "_nonEditable": true,
                "_title":"Library"
              }
            }
          },
          "${refid}0": {
            "page": "html content goes here in a real template"

          },
          "${refid}1": {
            "mylibrary": {
              "groupid": "${groupid}"
            }
          }
        },
        "${pid}1": {
          "structure0": {
            "participants":{
              "_ref":"${refid}2",
              "_order":0,
              "_title":"Participants",
              "_nonEditable": true,
              "main":{
                "_ref":"${refid}2",
                "_order":0,
                "_nonEditable": true,
                "_title":"Participants"
              }
            }
          },
          "${refid}2": {
            "page": "html content goes here in a real template"
          },
          "${refid}3": {
            "participants": {
              "groupid": "${groupid}"
            }
          }
        }
      },
      "structure": {
        "library": {
          "_title": "Library",
          "_order": 0,
          "_docref": "${pid}0",
          "_nonEditable": true,
          "_view": ["everyone", "anonymous", "-member"],
          "_edit": ["-manager"]
        },
        "participants": {
          "_title": "Participants",
          "_order": 1,
          "_docref": "${pid}1",
          "_nonEditable": true,
          "_view": ["everyone", "anonymous", "-member"],
          "_edit": ["-manager"]
        }
      }
    }
    eos

    data = {
      ":operation" => "import",
      ":contentType" => "json",
      ":content" => template
    }

    @s.execute_post(@s.url_for(path), data)

  end

end
