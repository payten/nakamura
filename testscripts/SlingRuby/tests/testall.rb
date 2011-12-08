#!/usr/bin/env ruby

require 'nakamura/test'
require 'logger'

SlingTest.setLogLevel(Logger::ERROR)

dir = File.dirname(__FILE__)
if not dir.match(/^\//)
  dir = "./#{dir}"
end

Dir.foreach(dir) do |path|
  if /.*\-test.rb/.match(File.basename(path))
    require "#{dir}/#{path}"
  end
end
