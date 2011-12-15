#!/usr/bin/env ruby

require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2274 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @m = Time.now.to_f.to_s.gsub('.', '')

    @u = create_test_user("kern2274")

    @other_user = create_test_user("otherUser")

    @third_user = create_test_user("thirdUser")

    @fourth_user = create_test_user("fourthUser")

    @template_path = "/var/templates/test/kern-2274/simple-test-template-" +@m

    create_template

    @s.switch_user(@u)

    @world_service = @s.url_for("/system/world/create")

  end

  def test_paramless_post_is_a_bad_request
    result = @s.execute_post(@world_service, {})
    assert_equal(400, result.code.to_i)
  end

  def test_valid_post
    group_id = "testgroup" + @m

    # group should not exist yet
    result = @s.execute_get(@s.url_for("/~" + group_id))
    assert_equal(404, result.code.to_i)

    result = @s.execute_post(@world_service, {
        "data" => '{
          "id": "' + group_id + '",
          "title" : "Group Title",
          "description" : "Group Description",
          "usersToAdd" :
          [
            { "userid" : "' + @other_user.name + '", "role" : "manager" },
            { "userid" : "' + @third_user.name + '", "role" : "member" },
            { "userid" : "' + @fourth_user.name + '", "role" : "member" }
          ],
          "tags" :
          [
            "tag1-' + group_id + '",
            "tag2-' + group_id + '"
          ],
          "worldTemplate" : "' + @template_path + '",
          "visibility": "public",
          "joinability": "no", 

          "message" : {
            "body" : "Hi \${firstName}, \${creatorName} has added you as a \${role} to the group \${groupName}",
            "subject" : "\${creatorName} has added you as a \${role} to the group \${groupName}",
            "creatorName" : "Joe Schmoe",
            "groupName" : "' + group_id + '",
            "system" : "Sakai OAE",
            "link" : "http://localhost:8080/~' + group_id + '",
            "toSend" : [
              {
                "userid" : "' + @other_user.name + '",
                "firstName" : "OtherUser",
                "role" : "Member",
                "messageMode" : "both"
              },
              {
                "userid" : "' + @third_user.name + '",
                "firstName" : "ThirdUser",
                "role" : "Manager",
                "messageMode" : "internal"
              },
              {
                "userid" : "' + @fourth_user.name + '",
                "firstName" : "FourthUser",
                "role" : "Member",
                "messageMode" : "external"
              }
            ]
          }

        }'
    })

    @log.info(result.body)
    assert_equal(200, result.code.to_i)

    # check the pooled content ids
    json = JSON.parse(result.body)
    for obj in json["results"] do
      if ( obj["pooledContentIDs"] != nil )
        for poolID in obj["pooledContentIDs"] do
          @log.info("Checking for existence of pooled content ID " + poolID)
          poolContent = @s.execute_get(@s.url_for("/p/" + poolID))
          assert_equal(200, poolContent.code.to_i)
        end
      end
    end

    # see that group and its subgroups exist
    result = @s.execute_get(@s.url_for("/~" + group_id + ".infinity.json"))
    assert_equal(200, result.code.to_i)
    json = JSON.parse(result.body)
    assert_equal(2, json["public"]["authprofile"]["sakai:tags"].length)

    # check for the tags
    result = @s.execute_get(@s.url_for("/tags/tag1-" + group_id + ".json"))
    assert_equal(200, result.code.to_i)
    result = @s.execute_get(@s.url_for("/tags/tag2-" + group_id + ".json"))
    assert_equal(200, result.code.to_i)

    # check the main group's data
    result = @s.execute_get(@s.url_for("/system/userManager/group/" + group_id + ".json"))
    assert_equal(200, result.code.to_i)
    json = JSON.parse(result.body)
    assert_equal("Group Title", json["properties"]["sakai:group-title"])
    assert_equal(1, json["properties"]["rep:group-managers"].length)
    assert_equal(3, json["properties"]["membersCount"])
    assert_equal(4, json["properties"]["rep:group-viewers"].length)
    assert_includes(json["properties"]["rep:group-viewers"], "everyone")
    assert_includes(json["properties"]["rep:group-viewers"], "anonymous")
    assert_equal("public", json["properties"]["sakai:group-visible"])
    assert_equal("no", json["properties"]["sakai:group-joinable"])

    # check for subgroups (-manager and -member)
    result = @s.execute_get(@s.url_for("/system/userManager/group/" + group_id + "-member.json"))
    assert_equal(200, result.code.to_i)
    json = JSON.parse(result.body)
    assert_equal("MEMBER", json["properties"]["sakai:role-title"])
    assert_equal(1, json["properties"]["rep:group-managers"].length)
    assert_equal(@third_user.name + ";" + @fourth_user.name, json["properties"]["members"])
    assert_includes(json["properties"]["rep:group-viewers"], "everyone")
    assert_includes(json["properties"]["rep:group-viewers"], "anonymous")
    assert_equal("public", json["properties"]["sakai:group-visible"])
    assert_equal("no", json["properties"]["sakai:group-joinable"])

    result = @s.execute_get(@s.url_for("/system/userManager/group/" + group_id + "-manager.json"))
    assert_equal(200, result.code.to_i)
    json = JSON.parse(result.body)
    assert_equal("MANAGER", json["properties"]["sakai:role-title"])
    assert_equal(1, json["properties"]["rep:group-managers"].length)
    assert_equal(@other_user.name, json["properties"]["members"])
    assert_includes(json["properties"]["rep:group-viewers"], "everyone")
    assert_includes(json["properties"]["rep:group-viewers"], "anonymous")
    assert_equal("public", json["properties"]["sakai:group-visible"])
    assert_equal("no", json["properties"]["sakai:group-joinable"])

  end

  def test_duplicate_creation_forbidden
    group_id = "redundantgroup" + @m

    # create it once
    result = @s.execute_post(@world_service, {
        "data" => '{
          "id": "' + group_id + '",
          "title" : "Redundant Group Title",
          "description" : "Group Description",
          "worldTemplate" : "' + @template_path + '",
          "visibility": "public",
          "joinability": "no"
        }'
    })
    @log.info(result.body)
    assert_equal(200, result.code.to_i)

    # create it again, should produce an error
    result = @s.execute_post(@world_service, {
        "data" => '{
          "id": "' + group_id + '",
          "title" : "Redundant Group Title",
          "description" : "Group Description",
          "worldTemplate" : "' + @template_path + '",
          "visibility": "public",
          "joinability": "no"
        }'
    })
    @log.info(result.body)
    json = JSON.parse(result.body)
    assert_equal(json["results"][0]["error"], "Group already exists")
  end

  def test_members_only_group
    group_id = "private" + @m

    result = @s.execute_post(@world_service, {
        "data" => '{
          "id": "' + group_id + '",
          "title" : "Group Title",
          "description" : "Group Description",
          "usersToAdd" :
          [
            { "userid" : "' + @other_user.name + '", "role" : "manager" },
            { "userid" : "' + @third_user.name + '", "role" : "member" },
            { "userid" : "' + @fourth_user.name + '", "role" : "member" }
          ],
          "tags" :
          [
            "tag1-' + group_id + '",
            "tag2-' + group_id + '"
          ],
          "worldTemplate" : "' + @template_path + '",
          "visibility": "members-only",
          "joinability": "yes"
        }'
    })

    @log.info(result.body)
    assert_equal(200, result.code.to_i)

    @s.switch_user(User.admin_user)

    # check the main group's data
    result = @s.execute_get(@s.url_for("/system/userManager/group/" + group_id + ".json"))
    json = JSON.parse(result.body)
    assert_equal("Group Title", json["properties"]["sakai:group-title"])
    assert_equal(1, json["properties"]["rep:group-managers"].length)
    assert_equal(2, json["properties"]["rep:group-viewers"].length)
    refute_includes(json["properties"]["rep:group-viewers"], "everyone")
    refute_includes(json["properties"]["rep:group-viewers"], "anonymous")
    assert_equal("members-only", json["properties"]["sakai:group-visible"])
    assert_equal("yes", json["properties"]["sakai:group-joinable"])

    # check for subgroups (-manager and -member)
    result = @s.execute_get(@s.url_for("/system/userManager/group/" + group_id + "-member.json"))
    assert_equal(200, result.code.to_i)
    json = JSON.parse(result.body)
    assert_equal("MEMBER", json["properties"]["sakai:role-title"])
    assert_equal(1, json["properties"]["rep:group-managers"].length)
    assert_equal(@third_user.name + ";" + @fourth_user.name, json["properties"]["members"])
    refute_includes(json["properties"]["rep:group-viewers"], "everyone")
    refute_includes(json["properties"]["rep:group-viewers"], "anonymous")
    assert_equal("members-only", json["properties"]["sakai:group-visible"])
    assert_equal("yes", json["properties"]["sakai:group-joinable"])

    result = @s.execute_get(@s.url_for("/system/userManager/group/" + group_id + "-manager.json"))
    assert_equal(200, result.code.to_i)
    json = JSON.parse(result.body)
    assert_equal("MANAGER", json["properties"]["sakai:role-title"])
    assert_equal(1, json["properties"]["rep:group-managers"].length)
    assert_equal(@other_user.name, json["properties"]["members"])
    refute_includes(json["properties"]["rep:group-viewers"], "everyone")
    refute_includes(json["properties"]["rep:group-viewers"], "anonymous")
    assert_equal("members-only", json["properties"]["sakai:group-visible"])
    assert_equal("yes", json["properties"]["sakai:group-joinable"])

  end


  def test_logged_in_only_group
    group_id = "loggedinonlygroup" + @m

    result = @s.execute_post(@world_service, {
        "data" => '{
          "id": "' + group_id + '",
          "title" : "Group Title",
          "description" : "Group Description",
          "usersToAdd" :
          [
            { "userid" : "' + @other_user.name + '", "role" : "manager" },
            { "userid" : "' + @third_user.name + '", "role" : "member" },
            { "userid" : "' + @fourth_user.name + '", "role" : "member" }
          ],
          "tags" :
          [
            "tag1-' + group_id + '",
            "tag2-' + group_id + '"
          ],
          "worldTemplate" : "' + @template_path + '",
          "visibility": "logged-in-only",
          "joinability": "withauth"
        }'
    })

    @log.info(result.body)
    assert_equal(200, result.code.to_i)

    # check the main group's data
    result = @s.execute_get(@s.url_for("/system/userManager/group/" + group_id + ".json"))
    json = JSON.parse(result.body)
    assert_equal("Group Title", json["properties"]["sakai:group-title"])
    assert_equal(1, json["properties"]["rep:group-managers"].length)
    assert_equal(3, json["properties"]["rep:group-viewers"].length)
    assert_includes(json["properties"]["rep:group-viewers"], "everyone")
    refute_includes(json["properties"]["rep:group-viewers"], "anonymous")
    assert_equal("logged-in-only", json["properties"]["sakai:group-visible"])
    assert_equal("withauth", json["properties"]["sakai:group-joinable"])

    # check for subgroups (-manager and -member)
    result = @s.execute_get(@s.url_for("/system/userManager/group/" + group_id + "-member.json"))
    assert_equal(200, result.code.to_i)
    json = JSON.parse(result.body)
    assert_equal("MEMBER", json["properties"]["sakai:role-title"])
    assert_equal(1, json["properties"]["rep:group-managers"].length)
    assert_equal(@third_user.name + ";" + @fourth_user.name, json["properties"]["members"])
    assert_includes(json["properties"]["rep:group-viewers"], "everyone")
    refute_includes(json["properties"]["rep:group-viewers"], "anonymous")
    assert_equal("logged-in-only", json["properties"]["sakai:group-visible"])
    assert_equal("withauth", json["properties"]["sakai:group-joinable"])

    result = @s.execute_get(@s.url_for("/system/userManager/group/" + group_id + "-manager.json"))
    assert_equal(200, result.code.to_i)
    json = JSON.parse(result.body)
    assert_equal("MANAGER", json["properties"]["sakai:role-title"])
    assert_equal(1, json["properties"]["rep:group-managers"].length)
    assert_equal(@other_user.name, json["properties"]["members"])
    assert_includes(json["properties"]["rep:group-viewers"], "everyone")
    refute_includes(json["properties"]["rep:group-viewers"], "anonymous")
    assert_equal("logged-in-only", json["properties"]["sakai:group-visible"])
    assert_equal("withauth", json["properties"]["sakai:group-joinable"])

  end

  def create_template

    template = '{
      "id": "simplegroup",
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
    }'

    data = {}
    data[':operation'] = 'import'
    data[':contentType'] = 'json'
    data[':content'] = template

    @s.execute_post(@s.url_for(@template_path), data)

  end
end
