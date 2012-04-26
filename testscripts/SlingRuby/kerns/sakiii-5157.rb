#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers

class TC_Sakiii5157 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @m = uniqueness
    @s.switch_user(User.admin_user)
    @world_service = @s.url_for("/system/world/create")
    @template_path = "/var/templates/test/kern-2558/simple-test-template-#{@m}"
    create_template(@template_path)
  end

  def test_create_group_with_schemaversion
    group_id = "testgroup#{@m}"
    schemaversion = "23"

    # Create a group with schemaVersion set
    result = @s.execute_post(@world_service, {
        "data" => <<-eos
        {
          "id": "#{group_id}",
          "title" : "Group Title",
          "description" : "Group Description",
          "worldTemplate" : "#{@template_path}",
          "visibility": "public",
          "joinability": "no",
          "schemaVersion": #{schemaversion}
        }
        eos
    })
    assert_equal(200, result.code.to_i)
    pid = get_pid(result)

    # grab the content data and verify the schemaVersion param has been set properly
    res = @s.execute_get(@s.url_for("/p/#{pid}.infinity.json"))
    assert_equal(200, res.code.to_i)
    json = JSON.parse(res.body)
    assert_equal(schemaversion, json['sakai:schemaversion'])
  end

  def test_create_group_without_schemaversion
    group_id = "testgroup#{@m}"

    # Create a group with no schemaVersion set
    result = @s.execute_post(@world_service, {
        "data" => <<-eos
        {
          "id": "#{group_id}",
          "title" : "Group Title",
          "description" : "Group Description",
          "worldTemplate" : "#{@template_path}",
          "visibility": "public",
          "joinability": "no"
        }
        eos
    })
    assert_equal(200, result.code.to_i)
    pid = get_pid(result)

    # grab the content data and verify the schemaVersion param has been set properly
    res = @s.execute_get(@s.url_for("/p/#{pid}.infinity.json"))
    assert_equal(200, res.code.to_i)
    json = JSON.parse(res.body)
    assert_nil(json['sakai:schemaversion'])
  end

  # get the pid from the results of creating a group
  def get_pid(result)
    results = JSON.parse(result.body)
    pid = nil
    results['results'].each do |request|
      if request.has_key? "pooledContentIDs"
        pid = request['pooledContentIDs'][0]
      end
    end
    pid
  end

  def create_template(path)

    template = <<-eos
    {
      "id": "simplegroupschemaversion",
      "title": "__MSG__SIMPLE_GROUP__",
      "img": "/dev/images/worldtemplates/simplegroup.png",
      "fullImg": "/dev/images/worldtemplates/simplegroup-full.png",
      "perfectFor": "__MSG__SIMPLE_GROUP_PERFECT_FOR__",
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
          "excludeSearch": true,
          "structure0": {
            "library": {
              "_ref": "${refid}0",
              "_order": 0,
              "_nonEditable": true,
              "_title": "Library",
              "main": {
                "_ref": "${refid}0",
                "_order": 0,
                "_nonEditable": true,
                "_title": "Library"
              }
            }
          },
          "${refid}0": {
            "rows": [
              {
                "columns": [
                  {
                    "width": 1,
                    "elements": [
                      {
                        "id": "${refid}1",
                        "type": "mylibrary"
                      }
                    ]
                  }
                ]
              }
            ],
            "${refid}1": {
              "mylibrary": {
                "groupid": "${groupid}"
              }
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
