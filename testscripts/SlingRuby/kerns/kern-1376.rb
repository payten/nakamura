#!/usr/bin/env ruby

# Add all files in testscripts\SlingRuby\lib directory to ruby "require" search path
require './ruby-lib-dir.rb'

require 'sling/test'
require 'sling/file'
include SlingUsers
include SlingFile

class TC_Kern1376Test < Test::Unit::TestCase
  include SlingTest

  def add_activity(url, appid, templateid, messagetext)
    res = @s.execute_post("#{url}.activity.json", {
      "sakai:activity-appid" => appid,
      "sakai:activity-templateid" => templateid,
      "sakai:activityMessage" => messagetext
    })
    assert_equal("200", res.code, "Should have added activity")
  end

  def test_get_pooled_content_activities
    @fm = FileManager.new(@s)
    m = Time.now.to_f.to_s.gsub('.', '')
    manager = create_user("user-manager-#{m}")
    @s.switch_user(manager)
    res = @fm.upload_pooled_file("random-#{m}.txt", "Plain content", "text/plain")
    assert_equal("201", res.code, "Expected to be able to create pooled content")
	uploadresult = JSON.parse(res.body)
	contentid = uploadresult["random-#{m}.txt"]['poolId']
	assert_not_nil(contentid, "Should have uploaded ID")
	contentpath = @s.url_for("/p/#{contentid}")

	# Add three activity notes.
    res = @s.execute_post("#{contentpath}.html", { "testing" => "testvalue" })
    assert_equal("200", res.code, " #{manager.name} should have been granted write to #{contentpath} ")
    @log.info(" #{manager.name} can write to the resource ")
    res = @s.execute_get("#{contentpath}.json")
    assert_equal("200", res.code, "Unable to get the metadata for the resource ")
    m = JSON.parse(res.body)
    assert_equal(m["testing"], "testvalue", "Looks like the property was not written Got #{res.body}")
    
	add_activity(contentpath, "status", "default", "First activity")
	add_activity(contentpath, "status", "default", "Second activity")
	add_activity(contentpath, "status", "default", "Third activity")

    wait_for_indexer()
    res = @s.execute_get(@s.url_for("/var/search/pool/activityfeed.json"), {
      "p" => "/p/#{contentid}"
    })
    assert_equal("200", res.code, "Should have found activity feed")
    @log.info("Activity feed is #{res.body}")
    activityfeed = JSON.parse(res.body)
    # we created three activities, but the system also created one
    # sakai:activityMessage=CREATED_FILE
    assert_equal(4, activityfeed["total"])
  end

end
