#!/bin/bash
set -e

git clone https://github.com/MrChartist/commodity-price-tracker.git
cd commodity-price-tracker
npx serve .
