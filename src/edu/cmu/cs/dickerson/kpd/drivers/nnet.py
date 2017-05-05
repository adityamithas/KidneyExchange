#!/usr/bin/env python
from __future__ import print_function
import numpy as np
import sys
import pandas as pd

#iteration = 0
#np.loadtxt( "st_1.csv", delimiter=',' )

#initial_state = pd.read_csv("st_1.csv", nrows=1)
initial_state = np.genfromtxt('st_1.csv', delimiter=',')
next_state = np.genfromtxt('st.csv', delimiter=',')

print(initial_state)
print (next_state)

#next_state = pd.read_csv("st.csv", nrows=1)
#current_file = pd.read_csv("weights.csv")
#if (current_file.size == 0):
#	current_file = np.zeros((len(initial_state)), dtype='float')
#current_file = []
#current_file = np.append(current_file, initial_state)
#current_file = np.append(current_file, next_state)
#iteration = iteration + 1
#print("ITERATION IS: %d" % iteration, file=sys.stderr)
#print("INITIAL_STATE IS: %s" % initial_state.__class__, file=sys.stderr)

#to_add = np.array([*initial_state[0]], dtype='float')
#current_file = np.vstack((current_file, to_add))
#to_add = np.array([*next_state[0]], dtype='float')
#current_file = np.vstack((current_file, to_add))
#df = pd.DataFrame(next_state)
#df.to_csv("weights.csv", sep='\t')
np.savetxt( 'weights.csv', next_state, delimiter=',' )

#merge = initial_state.append(next_state, ignore_index=True)
#merge.to_csv('weights.csv', index=False)



