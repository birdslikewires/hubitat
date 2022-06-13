#!/usr/bin/env bash

## bundler.sh v1.00 (13th June 2022)
##  Makes a bundle with a library in it so you don't have to do it by hand.

pathtothisscript="$(cd "$(dirname "$0")";pwd -P)"

zip -jD "$pathtothisscript/library.zip" "$pathtothisscript/BirdsLikeWires.library/"*
