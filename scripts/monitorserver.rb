#!/usr/bin/env ruby

require 'rubygems'
require 'daemons'

Daemons.run('preview_processor.rb')
